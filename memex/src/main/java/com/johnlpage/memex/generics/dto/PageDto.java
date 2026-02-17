package com.johnlpage.memex.generics.dto;

import java.util.List;

import lombok.Getter;
import org.springframework.data.domain.Slice;

/**
 * @param <T> This exists because Spring does not like you serializing raw page objects, We use
 *            Slice not Page because fetching a page does a count of all documents which is clearly quite
 *            expensive, in general paging results is not efficient except in specific cases where you can
 *            work to optimize it.
 */
@Getter
public class PageDto<T> {
    private final List<T> content;
    private final int pageNumber;
    private final int pageSize;

    // private int totalPages;
    // private long totalElements;

    public PageDto(Slice<T> returnPage) {
        this.content = returnPage.getContent();
        this.pageNumber = returnPage.getPageable().getPageNumber();
        this.pageSize = returnPage.getPageable().getPageSize();

        // If you want these use Page<T> not Slice<T> but this needs
        // to perform a full count for each page
        // Which is definitely not efficient.

        // this.totalPages = returnPage.getTotalPages();
        // this.totalElements = returnPage.getTotalElements();

    }
}
