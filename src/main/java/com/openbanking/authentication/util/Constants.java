package com.openbanking.authentication.util;

public class Constants {

    public static enum BUSINESS_ERROR {
        CLIENT_NOT_FOUND,
        MFA_NOT_ENABLED,
        ORGANIZATION_EMPTY,
        ORGANIZATION_NOT_FOUND,
        MFA_NOT_COMPLETED
    }

    public static String MFA_INPROGRESS = "MFA_INPROGRESS";
    public static String MFA_COMPLETED = "MFA_COMPLETED";
    public static String MFA_FORCE_LOGOUT = "MFA_FORCE_LOGOUT";

}