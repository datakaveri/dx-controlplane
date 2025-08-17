package org.cdpg.dx.database.elastic.model;

import java.util.List;

public class QueryDecoderRequestDTO {
  private String searchType;
  private Integer size;
  private Integer page;
  private String id;
  private String organisationId;
  private String publishStatus;
  private List<String> filter;
  private TextSearchRequestDTO textSearchRequest;
  private SearchCriteriaRequestDTO searchCriteriaRequest;
  private AccessPolicyRequestDTO accessPolicyRequest;
  private InstanceFilterRequestDTO instanceFilterRequest;
  private ResponseFilterRequestDTO responseFilterRequest;
  private List<OrderBy> sort;
  private String requestType;
  public QueryDecoderRequestDTO(
      String searchType,
      Integer size,
      Integer page,
      String id,
      List<String> filter,
      TextSearchRequestDTO textSearchRequest,
      SearchCriteriaRequestDTO searchCriteriaRequest,
      AccessPolicyRequestDTO accessPolicyRequest,
      InstanceFilterRequestDTO instanceFilterRequest,
      ResponseFilterRequestDTO responseFilterRequest,
      List<OrderBy> sort,String requestType) {
    this.searchType = searchType;
    this.size = size;
    this.page = page;
    this.id = id;
    this.filter = filter;
    this.textSearchRequest = textSearchRequest;
    this.searchCriteriaRequest = searchCriteriaRequest;
    this.accessPolicyRequest = accessPolicyRequest;
    this.instanceFilterRequest = instanceFilterRequest;
    this.responseFilterRequest = responseFilterRequest;
    this.sort = sort;
    this.requestType=requestType;
  }

  public QueryDecoderRequestDTO(
          Integer size,
          Integer page,
          List<OrderBy> sort,
          String organizationId,
          String publishStatus,
          String requestType
  ) {
    this.size = size;
    this.page = page;
    this.sort = sort;
    this.organisationId = organizationId;
    this.publishStatus = publishStatus;
    this.requestType=requestType;
  }
  public List<OrderBy> getSort() {
    return sort;
  }

  public void setSort(List<OrderBy> sort) {
    this.sort = sort;
  }

  public Integer getSize() {
    return size;
  }

  public void setSize(Integer size) {
    this.size = size;
  }

  public Integer getPage() {
    return page;
  }

  public void setPage(Integer page) {
    this.page = page;
  }

  public String getPublishStatus() {
    return publishStatus;
  }

  public void setPublishStatus(String publishStatus) {
    this.publishStatus = publishStatus;
  }

  public String getSearchType() {
    return searchType;
  }

  public void setSearchType(String searchType) {
    this.searchType = searchType;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public List<String> getFilter() {
    return filter;
  }

  public void setFilter(List<String> filter) {
    this.filter = filter;
  }

  public String getOrganisationId() {
    return organisationId;
  }

  public String getRequestType() {
    return requestType;
  }
  public TextSearchRequestDTO getTextSearchRequest() {
    return textSearchRequest;
  }

  public void setTextSearchRequest(TextSearchRequestDTO textSearchRequest) {
    this.textSearchRequest = textSearchRequest;
  }

  public SearchCriteriaRequestDTO getSearchCriteriaRequest() {
    return searchCriteriaRequest;
  }

  public void setSearchCriteriaRequest(SearchCriteriaRequestDTO searchCriteriaRequest) {
    this.searchCriteriaRequest = searchCriteriaRequest;
  }

  public AccessPolicyRequestDTO getAccessPolicyRequest() {
    return accessPolicyRequest;
  }

  public void setAccessPolicyRequest(AccessPolicyRequestDTO accessPolicyRequest) {
    this.accessPolicyRequest = accessPolicyRequest;
  }

  public InstanceFilterRequestDTO getInstanceFilterRequest() {
    return instanceFilterRequest;
  }

  public void setInstanceFilterRequest(InstanceFilterRequestDTO instanceFilterRequest) {
    this.instanceFilterRequest = instanceFilterRequest;
  }

  public ResponseFilterRequestDTO getResponseFilterRequest() {
    return responseFilterRequest;
  }

  public void setResponseFilterRequest(ResponseFilterRequestDTO responseFilterRequest) {
    this.responseFilterRequest = responseFilterRequest;
  }
}
