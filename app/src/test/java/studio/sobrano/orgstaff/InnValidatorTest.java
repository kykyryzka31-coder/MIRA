package studio.sobrano.orgstaff;

import org.junit.Test;
import static org.junit.Assert.*;

public class InnValidatorTest {
    @Test public void knownCorporateInnsAreValid() {
        assertTrue(InnValidator.isValid("7707083893"));
        assertTrue(InnValidator.isValid("7736050003"));
        assertTrue(InnValidator.isValid("7708004767"));
    }

    @Test public void malformedInnsAreRejected() {
        assertFalse(InnValidator.isValid("123"));
        assertFalse(InnValidator.isValid("abcdefghij"));
        assertFalse(InnValidator.isValid("7707083894"));
    }
}
