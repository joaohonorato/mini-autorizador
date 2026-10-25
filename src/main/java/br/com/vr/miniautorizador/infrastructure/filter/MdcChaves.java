package br.com.vr.miniautorizador.infrastructure.filter;

final class MdcChaves {

    static final String TRACE_ID = "traceId";
    static final String NUMERO_CARTAO = "numeroCartao";
    static final String HTTP_METHOD = "httpMethod";
    static final String REQUEST_URI = "requestUri";
    static final String HTTP_STATUS = "httpStatus";

    private MdcChaves() {
    }
}
