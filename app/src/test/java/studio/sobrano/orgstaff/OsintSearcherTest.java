package studio.sobrano.orgstaff;

import org.junit.Test;
import static org.junit.Assert.*;

public class OsintSearcherTest {
    @Test public void extractsFullNameNearRole() {
        String text = "Генеральный директор Иванов Алексей Сергеевич возглавляет организацию.";
        assertEquals("Иванов Алексей Сергеевич", OsintSearcher.extractNameNearRole("Генеральный директор", text));
    }

    @Test public void extractsInitialName() {
        String text = "Главный бухгалтер: Петрова Е. В. Данные опубликованы в отчете.";
        assertEquals("Петрова Е. В.", OsintSearcher.extractNameNearRole("Главный бухгалтер", text));
    }
}
