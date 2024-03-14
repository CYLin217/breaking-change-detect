package uk.co.sainsburys.breakingchangedetect.entity.dto;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;
import uk.co.sainsburys.breakingchangedetect.entity.Entry;
import uk.co.sainsburys.breakingchangedetect.entity.Type;

import java.util.Set;

@Data
@Jacksonized
@Builder
public class DifferenceCase {

    private Type type;

    private Entry entry;

    private String endPoint;
}
