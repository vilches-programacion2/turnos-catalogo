package catalogo.replica.infrastructure.persistence.adapter;

import java.util.Optional;
import java.util.List;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import catalogo.replica.domain.model.CategoriaProfesional;
import catalogo.replica.domain.model.EstadoCatalogo;
import catalogo.replica.domain.model.HorarioSemanal;
import catalogo.replica.domain.model.LecturaProfesional;
import catalogo.replica.domain.model.Profesional;
import catalogo.replica.domain.ports.out.CatalogoRepository;
import catalogo.replica.infrastructure.persistence.mapper.CategoriaProfesionalMapper;
import catalogo.replica.infrastructure.persistence.mapper.EstadoCatalogoMapper;
import catalogo.replica.infrastructure.persistence.mapper.HorarioSemanalMapper;
import catalogo.replica.infrastructure.persistence.mapper.ProfesionalMapper;
import catalogo.replica.infrastructure.persistence.repository.JpaCategoriaProfesionalRepository;
import catalogo.replica.infrastructure.persistence.repository.JpaEstadoCatalogoRepository;
import catalogo.replica.infrastructure.persistence.repository.JpaHorarioSemanalRepository;
import catalogo.replica.infrastructure.persistence.repository.JpaProfesionalRepository;

@Component
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
}
