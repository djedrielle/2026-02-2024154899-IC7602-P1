package com.tec.dnsapi.dto;

import java.util.List;

public record TargetRequest(
    String record_name,
    String ip_address,
    Integer port,
    String check_type,
    Integer timeout_ms,
    Integer retries,
    String http_path,
    List<Integer> expected_status_codes,
    String basic_auth_user,
    String basic_auth_pass
) {}
