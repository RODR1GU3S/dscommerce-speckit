package com.devsuperior.dscommerce.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Input for own-order listing only; defaults apply when a parameter is absent. */
public class OwnOrderPageRequestDTO {

    @NotNull
    @Min(0)
    private Integer page = 0;

    @NotNull
    @Min(1)
    @Max(50)
    private Integer size = 12;

    public OwnOrderPageRequestDTO() { }

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }

    @JsonIgnore
    @AssertTrue(message = "Order page offset exceeds the supported range")
    public boolean isOffsetWithinRange() {
        if (page == null || page < 0 || size == null || size < 1 || size > 50) {
            return true; // The field constraints diagnose these inputs independently.
        }
        return ((long) page) * size <= Integer.MAX_VALUE;
    }
}
