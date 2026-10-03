package studio.sobrano.orgstaff;

import java.util.*;

final class SourceRef {
    final String title;
    final String url;
    final String snippet;

    SourceRef(String title, String url, String snippet) {
        this.title = title == null ? "Источник" : title.trim();
        this.url = url == null ? "" : url.trim();
        this.snippet = snippet == null ? "" : snippet.trim();
    }

    String domain() {
        try { return new java.net.URL(url).getHost().replace("www.", ""); }
        catch (Exception e) { return ""; }
    }
}

final class PersonRecord {
    final String role;
    final String name;
    final List<SourceRef> sources = new ArrayList<>();
    private Boolean serverConfirmed;
    String confidence = "";

    PersonRecord(String role, String name) {
        this.role = role == null ? "" : role.trim();
        this.name = name == null ? "" : name.trim();
    }

    void setServerStatus(boolean confirmed, String confidence) {
        this.serverConfirmed = confirmed;
        this.confidence = confidence == null ? "" : confidence;
    }

    boolean confirmed() {
        if (serverConfirmed != null) return serverConfirmed;
        Set<String> domains = new HashSet<>();
        for (SourceRef s : sources) if (!s.domain().isEmpty()) domains.add(s.domain());
        return domains.size() >= 2;
    }

    boolean officialRegistry() { return "official-registry".equals(confidence); }
    boolean officialSite() { return "official-site".equals(confidence); }
}

final class OrganizationInfo {
    String name = "";
    String legalName = "";
    String ogrn = "";
    String kpp = "";
    String region = "";
    String legalAddress = "";
    String website = "";
    String registrationDate = "";
    String createdDate = "";
    String age = "";
    String staffCount = "";
    String foundersCount = "";
    String capital = "";
    String okvedCode = "";
    String okvedText = "";

    void mergeFrom(OrganizationInfo other) {
        if (other == null) return;
        if (!other.name.isBlank()) name = other.name;
        if (!other.legalName.isBlank()) legalName = other.legalName;
        if (!other.ogrn.isBlank()) ogrn = other.ogrn;
        if (!other.kpp.isBlank()) kpp = other.kpp;
        if (!other.region.isBlank()) region = other.region;
        if (!other.legalAddress.isBlank()) legalAddress = other.legalAddress;
        if (!other.website.isBlank()) website = other.website;
        if (!other.registrationDate.isBlank()) registrationDate = other.registrationDate;
        if (!other.createdDate.isBlank()) createdDate = other.createdDate;
        if (!other.age.isBlank()) age = other.age;
        if (!other.staffCount.isBlank()) staffCount = other.staffCount;
        if (!other.foundersCount.isBlank()) foundersCount = other.foundersCount;
        if (!other.capital.isBlank()) capital = other.capital;
        if (!other.okvedCode.isBlank()) okvedCode = other.okvedCode;
        if (!other.okvedText.isBlank()) okvedText = other.okvedText;
    }
}

final class SearchResult {
    String inn;
    String orgName;
    String error;
    final OrganizationInfo organization = new OrganizationInfo();
    final List<PersonRecord> people = new ArrayList<>();
    final List<SourceRef> sources = new ArrayList<>();
}

final class PersonProfile {
    String name = "";
    String role = "";
    String birthDate = "";
    String age = "";
    String birthplace = "";
    String workSince = "";
    String tenure = "";
    String error;
    final List<String> education = new ArrayList<>();
    final List<String> career = new ArrayList<>();
    final List<SourceRef> sources = new ArrayList<>();
}
