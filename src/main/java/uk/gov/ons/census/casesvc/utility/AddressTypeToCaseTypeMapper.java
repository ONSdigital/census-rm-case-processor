package uk.gov.ons.census.casesvc.utility;

public class AddressTypeToCaseTypeMapper {

    public static String mapFromAddressTypeToCaseType(String addresssType) {
        switch(addresssType) {
            case "HH":
                return "H";
            case "CE":
                return "C";
            default:
                return addresssType;
        }
    }
}
