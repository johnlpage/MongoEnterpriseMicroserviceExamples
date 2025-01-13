package com.johnlpage.mews.dto;

import java.util.List;
import lombok.Getter;
import org.springframework.data.domain.Slice;

@Getter
public class PageDto<T> {
  private List<T> content;
  private int totalPages;
  private long totalElements;
  private int pageNumber;
  private int pageSize;

  public PageDto(Slice<T> returnPage) {
    this.content = returnPage.getContent();
    this.pageNumber = returnPage.getPageable().getPageNumber();
    this.pageSize = returnPage.getPageable().getPageSize();

    // If you want these use Page<T> but this need a full count for each query
    // Which is definately not efficient.

    // this.totalPages = returnPage.getTotalPages();
    // this.totalElements = returnPage.getTotalElements();

  }
}
