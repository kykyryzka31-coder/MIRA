package studio.sobrano.orgstaff;

public final class InnValidator {
    private InnValidator() {}

    public static boolean isValid(String inn) {
        if (inn == null || !inn.matches("\\d{10}|\\d{12}")) return false;
        int[] d = new int[inn.length()];
        for (int i = 0; i < inn.length(); i++) d[i] = inn.charAt(i) - '0';
        if (d.length == 10) {
            int[] w = {2,4,10,3,5,9,4,6,8};
            return checksum(d, w) == d[9];
        }
        int[] w11 = {7,2,4,10,3,5,9,4,6,8};
        int[] w12 = {3,7,2,4,10,3,5,9,4,6,8};
        return checksum(d, w11) == d[10] && checksum(d, w12) == d[11];
    }

    private static int checksum(int[] d, int[] w) {
        int sum = 0;
        for (int i = 0; i < w.length; i++) sum += d[i] * w[i];
        return (sum % 11) % 10;
    }
}
