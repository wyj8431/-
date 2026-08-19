package com.example.lowcode.auth.domain;

public class User {
    private Long id;
    private String phone;
    private String status;
    private int securityVersion;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getSecurityVersion() {
        return securityVersion;
    }

    public void setSecurityVersion(int securityVersion) {
        this.securityVersion = securityVersion;
    }
}
