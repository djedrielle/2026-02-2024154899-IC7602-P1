package com.tec.dnsapi.dto;

import java.util.List;
import java.util.Map;

/**
 * Body recibido en POST /api/records y PUT /api/records/{name}.
 * Refleja exactamente el objeto que envía la DNS UI (función recordToApi).
 */
public record RecordRequest(
                String name,
                String type,
                Integer ttl,
                List<Map<String, Object>> ips) {
}
