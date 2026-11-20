package catalogo;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import catalogo.replica.domain.model.CategoriaProfesional;
import catalogo.replica.domain.model.HorarioSemanal;
import catalogo.replica.domain.model.Profesional;
import catalogo.replica.domain.model.SnapshotCatalogo;

final class SnapshotTestData {
    static final Instant FECHA = Instant.parse("2026-09-10T13:40:00Z");
    static final String JSON = """
            {"snapshotVersion":7,"generatedAt":"2026-09-10T13:40:00Z",
             "professionalCategories":[{"id":1,"name":"Clínica","description":null,"enabled":true,
              "createdAt":"2026-09-10T13:40:00Z","updatedAt":"2026-09-10T13:40:00Z"}],
             "professionals":[{"id":15,"categoryId":1,"firstName":"Ana","lastName":"Gómez","enabled":true,
              "createdAt":"2026-09-10T13:40:00Z","updatedAt":"2026-09-10T13:40:00Z"}],
             "weeklySchedules":[{"id":21,"professionalId":15,"dayOfWeek":"MONDAY",
              "startTime":"09:00","endTime":"12:00:00","slotDurationMinutes":30,"enabled":true,
              "createdAt":"2026-09-10T13:40:00Z","updatedAt":"2026-09-10T13:40:00Z"}],
             "campoCompatibleNuevo":true}
            """;

    private SnapshotTestData() {
    }

    static SnapshotCatalogo snapshot(long version, long offset) {
        var categoria = CategoriaProfesional.builder().id(1 + offset).name("Clínica").description(null)
                .enabled(true).createdAt(FECHA).updatedAt(FECHA).build();
        var profesional = Profesional.builder().id(15 + offset).categoryId(categoria.getId())
                .firstName("Ana").lastName("Gómez").enabled(true).createdAt(FECHA).updatedAt(FECHA).build();
        var horario = HorarioSemanal.builder().id(21 + offset).professionalId(profesional.getId())
                .dayOfWeek(DayOfWeek.MONDAY).startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(12, 0))
                .slotDurationMinutes(30).enabled(true).createdAt(FECHA).updatedAt(FECHA).build();
        return SnapshotCatalogo.builder().snapshotVersion(version).generatedAt(FECHA)
                .professionalCategories(List.of(categoria)).professionals(List.of(profesional))
                .weeklySchedules(List.of(horario)).build();
    }

    static SnapshotCatalogo vacio(long version) {
        return SnapshotCatalogo.builder().snapshotVersion(version).generatedAt(FECHA)
                .professionalCategories(List.of()).professionals(List.of()).weeklySchedules(List.of()).build();
    }
}
