package com.johnlpage.mews.dto;

import java.util.List;
import org.springframework.data.domain.Slice;

public class PageDTO<T> {
    private List<T> content;
    private int totalPages;
    private long totalElements;
    private int pageNumber;
    private int pageSize;

    public List<T> getContent() {
        return content;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public int getPageSize() {
        return pageSize;
    }

    public PageDTO(Slice<T> returnPage) {
        this.content = returnPage.getContent();
        this.pageNumber = returnPage.getPageable().getPageNumber();
        this.pageSize = returnPage.getPageable().getPageSize();

    // If you want these use Page<T> but this need a full count for each query
    // Which is definitely not efficient.

    // this.totalPages = returnPage.getTotalPages();
    // this.totalElements = returnPage.getTotalElements();

  }
}
