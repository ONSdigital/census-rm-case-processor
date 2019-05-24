package uk.gov.ons.census.casesvc.utility;

import org.junit.Test;

import static org.assertj.core.api.Java6Assertions.assertThat;

public class AddressTypeToCaseTypeMapperTest {

    @Test
    public void testMappings() {
        assertThat(AddressTypeToCaseTypeMapper.mapFromAddressTypeToCaseType("HH")).isEqualTo("H");
        assertThat(AddressTypeToCaseTypeMapper.mapFromAddressTypeToCaseType("CE")).isEqualTo("C");
        assertThat(AddressTypeToCaseTypeMapper.mapFromAddressTypeToCaseType("CI")).isEqualTo("CI");
        assertThat(AddressTypeToCaseTypeMapper.mapFromAddressTypeToCaseType("HI")).isEqualTo("HI");
    }
}
