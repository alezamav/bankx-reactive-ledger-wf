package com.bankx.risk;

/**
 * Resultado de la evaluación de riesgo.
 *
 * <p>Además de si la operación se permite, transporta el <b>origen</b> de la
 * decisión. Sin ese dato no hay forma de distinguir, desde la respuesta, una
 * evaluación del servicio remoto de una resuelta por el fallback al legado,
 * que es precisamente lo que hace observable al circuit breaker.</p>
 *
 * @param allowed true si la operación se permite
 * @param source  {@link #REMOTE} o {@link #LEGACY_FALLBACK}
 */
public record RiskDecision(boolean allowed, String source) {

    /** La decisión provino del servicio remoto de riesgo. */
    public static final String REMOTE = "REMOTE";

    /** El remoto falló o excedió el timeout; resolvió el módulo legado. */
    public static final String LEGACY_FALLBACK = "LEGACY_FALLBACK";

    public static RiskDecision remote(boolean allowed) {
        return new RiskDecision(allowed, REMOTE);
    }

    public static RiskDecision legacyFallback(boolean allowed) {
        return new RiskDecision(allowed, LEGACY_FALLBACK);
    }
}
