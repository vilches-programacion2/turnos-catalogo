package catalogo.replica.infrastructure.persistence.adapter;

import java.util.Optional;
import java.util.List;
import java.util.Comparator;
import java.util.HashMap;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import catalogo.replica.domain.model.CategoriaProfesional;
import catalogo.replica.domain.model.EstadoCatalogo;
import catalogo.replica.domain.model.HorarioSemanal;
import catalogo.replica.domain.model.LecturaProfesional;
import catalogo.replica.domain.model.Profesional;
import catalogo.replica.domain.model.SnapshotCatalogo;
import catalogo.replica.domain.model.FiltroProfesionales;
import catalogo.replica.domain.model.LecturaBusquedaProfesionales;
import catalogo.replica.domain.model.LecturaCategorias;
import catalogo.replica.infrastructure.persistence.entity.ProfesionalEntity;
import catalogo.replica.application.exception.SnapshotCatalogoException;
import catalogo.replica.application.exception.SnapshotCatalogoException.Error;
import catalogo.replica.domain.ports.out.CatalogoRepository;
import catalogo.replica.infrastructure.persistence.mapper.CategoriaProfesionalMapper;
import catalogo.replica.infrastructure.persistence.mapper.EstadoCatalogoMapper;
import catalogo.replica.infrastructure.persistence.mapper.HorarioSemanalMapper;
import catalogo.replica.infrastructure.persistence.mapper.ProfesionalMapper;
import catalogo.replica.infrastructure.persistence.repository.JpaCategoriaProfesionalRepository;
import catalogo.replica.infrastructure.persistence.repository.JpaEstadoCatalogoRepository;
import catalogo.replica.infrastructure.persistence.repository.JpaHorarioSemanalRepository;
import catalogo.replica.infrastructure.persistence.repository.JpaProfesionalRepository;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaCatalogoRepositoryAdapter implements CatalogoRepository {
    private final JpaCategoriaProfesionalRepository categorias;
    private final JpaProfesionalRepository profesionales;
    private final JpaHorarioSemanalRepository horarios;
    private final JpaEstadoCatalogoRepository estados;
    private final CategoriaProfesionalMapper categoriaMapper;
    private final ProfesionalMapper profesionalMapper;
    private final HorarioSemanalMapper horarioMapper;
    private final EstadoCatalogoMapper estadoMapper;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public CategoriaProfesional guardarCategoria(CategoriaProfesional categoria) {
        return categoriaMapper.toDomainModel(categorias.saveAndFlush(categoriaMapper.toEntity(categoria)));
    }

    @Override
    public Optional<CategoriaProfesional> buscarCategoria(Long id) {
        return categorias.findById(id).map(categoriaMapper::toDomainModel);
    }

    @Override
    @Transactional
    public Profesional guardarProfesional(Profesional profesional) {
        return profesionalMapper.toDomainModel(profesionales.saveAndFlush(profesionalMapper.toEntity(profesional)));
    }

    @Override
    public Optional<Profesional> buscarProfesional(Long id) {
        return profesionales.findById(id).map(profesionalMapper::toDomainModel);
    }

    @Override
    @Transactional
    public HorarioSemanal guardarHorario(HorarioSemanal horario) {
        return horarioMapper.toDomainModel(horarios.saveAndFlush(horarioMapper.toEntity(horario)));
    }

    @Override
    public Optional<HorarioSemanal> buscarHorario(Long id) {
        return horarios.findById(id).map(horarioMapper::toDomainModel);
    }

    @Override
    public Optional<EstadoCatalogo> obtenerEstado() {
        return estados.findById(1L).map(estadoMapper::toDomainModel);
    }

    @Override
    @Transactional
    public EstadoCatalogo guardarEstado(EstadoCatalogo estado) {
        return estadoMapper.toDomainModel(estados.saveAndFlush(estadoMapper.toEntity(estado)));
    }

    @Override
    @Transactional
    public EstadoCatalogo reemplazarSnapshot(SnapshotCatalogo snapshot) {
        // Serializa reemplazos incluso cuando todavía no existe la fila de checkpoint.
        // Los escritores incrementales deberán tomar este mismo bloqueo antes de escribir.
        entityManager.createNativeQuery("LOCK TABLE catalog_state IN SHARE ROW EXCLUSIVE MODE").executeUpdate();
        entityManager.clear();
        var actual = estados.findById(1L);
        if (actual.isPresent() && actual.get().getVersionAplicada() > snapshot.getSnapshotVersion()) {
            throw new SnapshotCatalogoException(Error.VERSION_OBSOLETA);
        }
        horarios.deleteAllInBatch();
        profesionales.deleteAllInBatch();
        categorias.deleteAllInBatch();
        entityManager.clear();
        categorias.saveAllAndFlush(snapshot.getProfessionalCategories().stream().map(categoriaMapper::toEntity).toList());
        profesionales.saveAllAndFlush(snapshot.getProfessionals().stream().map(profesionalMapper::toEntity).toList());
        horarios.saveAllAndFlush(snapshot.getWeeklySchedules().stream().map(horarioMapper::toEntity).toList());
        var estado = EstadoCatalogo.builder().versionAplicada(snapshot.getSnapshotVersion())
                .actualizadoEn(Instant.now().truncatedTo(ChronoUnit.MICROS)).build();
        return estadoMapper.toDomainModel(estados.saveAndFlush(estadoMapper.toEntity(estado)));
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LecturaProfesional leerProfesional(Long id) {
        var estado = estados.findById(1L).map(estadoMapper::toDomainModel).orElse(null);
        if (estado == null) {
            return LecturaProfesional.builder().horarios(List.of()).build();
        }
        var profesional = profesionales.findById(id).map(profesionalMapper::toDomainModel).orElse(null);
        List<HorarioSemanal> agenda = profesional == null ? List.of()
                : horarios.findByProfessionalIdAndEnabledTrue(id).stream()
                        .map(horarioMapper::toDomainModel)
                        .sorted(Comparator.comparing(HorarioSemanal::getDayOfWeek)
                                .thenComparing(HorarioSemanal::getStartTime).thenComparing(HorarioSemanal::getId))
                        .toList();
        return LecturaProfesional.builder().estado(estado).profesional(profesional).horarios(agenda).build();
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LecturaCategorias listarCategorias() {
        var estado = estados.findById(1L).map(estadoMapper::toDomainModel).orElse(null);
        var contenido = estado == null ? List.<CategoriaProfesional>of()
                : categorias.findAll().stream().map(categoriaMapper::toDomainModel)
                        .sorted(Comparator.comparing(CategoriaProfesional::getName, String.CASE_INSENSITIVE_ORDER)
                                .thenComparing(CategoriaProfesional::getId)).toList();
        return LecturaCategorias.builder().estado(estado).categorias(contenido).build();
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LecturaBusquedaProfesionales buscarProfesionales(FiltroProfesionales filtro) {
        var estado = estados.findById(1L).map(estadoMapper::toDomainModel).orElse(null);
        boolean categoriaValida = estado != null
                && (filtro.getCategoryId() == null || categorias.existsById(filtro.getCategoryId()));
        var resultado = LecturaBusquedaProfesionales.builder().estado(estado).categoriaValida(categoriaValida)
                .profesionales(List.of()).page(filtro.getPage()).size(filtro.getSize()).totalElements(0).build();
        if (estado == null || !categoriaValida) return resultado;

        var where = new StringBuilder(" FROM professional p WHERE 1=1");
        var parametros = new HashMap<String, Object>();
        if (filtro.getCategoryId() != null) {
            where.append(" AND p.category_id = :categoria");
            parametros.put("categoria", filtro.getCategoryId());
        }
        if (filtro.getEnabled() != null) {
            where.append(" AND p.enabled = :habilitado");
            parametros.put("habilitado", filtro.getEnabled());
        }
        if (filtro.getName() != null && !filtro.getName().isBlank()) {
            where.append(" AND LOWER(p.first_name || ' ' || p.last_name) LIKE LOWER(:nombre) ESCAPE '!'");
            String texto = filtro.getName().strip().replace("!", "!!").replace("%", "!%").replace("_", "!_");
            parametros.put("nombre", "%" + texto + "%");
        }
        if (filtro.getDayOfWeek() != null) {
            where.append("""
                     AND p.enabled = true AND EXISTS (
                         SELECT 1 FROM weekly_schedule s
                         WHERE s.professional_id = p.id AND s.enabled = true AND s.day_of_week = :dia
                         AND EXTRACT(EPOCH FROM (s.end_time - s.start_time)) >= s.slot_duration_minutes * 60
                    """);
            parametros.put("dia", filtro.getDayOfWeek().name());
            if (filtro.getTime() != null) {
                where.append("""
                         AND CAST(:hora AS time) >= s.start_time
                         AND EXTRACT(EPOCH FROM (s.end_time - CAST(:hora AS time))) >= s.slot_duration_minutes * 60
                         AND MOD(EXTRACT(EPOCH FROM (CAST(:hora AS time) - s.start_time)), s.slot_duration_minutes * 60) = 0
                        """);
                parametros.put("hora", filtro.getTime());
            }
            where.append(")");
        }
        var session = entityManager.unwrap(Session.class);
        var count = session.createNativeQuery("SELECT COUNT(*)" + where, Long.class);
        var query = session.createNativeQuery("SELECT p.*" + where
                + " ORDER BY LOWER(p.last_name), LOWER(p.first_name), p.id", ProfesionalEntity.class);
        parametros.forEach((key, value) -> {
            count.setParameter(key, value);
            query.setParameter(key, value);
        });
        resultado.setTotalElements(count.getSingleResult());
        resultado.setProfesionales(query.setFirstResult(Math.toIntExact((long) filtro.getPage() * filtro.getSize()))
                .setMaxResults(filtro.getSize()).getResultList().stream().map(profesionalMapper::toDomainModel).toList());
        return resultado;
    }
}
