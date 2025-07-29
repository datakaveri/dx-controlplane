package org.cdpg.dx.aaa.search.service;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.search.util.ResponseModel;
import org.cdpg.dx.database.elastic.model.QueryDecoderRequestDTO;

public interface SearchService {

  Future<ResponseModel> postSearch(QueryDecoderRequestDTO queryDecoder);

  Future<ResponseModel> postCount(QueryDecoderRequestDTO queryDecoder);
}
