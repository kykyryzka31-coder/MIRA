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
        this.role = role;
        this.name = name;
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

    boolean officialRegistry() {
        return "official-registry".equals(confidence);
    }
}

final class OrganizationInfo {
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
}

final class SearchResult {
    String inn;
    String orgName;
    String error;
    OrganizationInfo organization = new OrganizationInfo();
    final List<PersonRecord> people = new ArrayList<>();
    final List<SourceRef> sources = new ArrayList<>();
}

final class PeopleResult {
    String error;
    final List<PersonRecord> people = new ArrayList<>();
    final List<SourceRef> sources = new ArrayList<>();
}

final class PersonProfile {
    String error;
    String birthDate = "";
    String age = "";
    String birthplace = "";
    String workSince = "";
    String tenure = "";
    String privacy = "";
    final List<String> education = new ArrayList<>();
    final List<String> career = new ArrayList<>();
    final List<SourceRef> sources = new ArrayList<>();
}
