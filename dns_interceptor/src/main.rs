use std::net::UdpSocket;
use std::net::Ipv4Addr;
use std::env;
use std::error::Error;
use std::sync::Arc;
use std::thread;
use base64::Engine;
use base64::engine::general_purpose::STANDARD;
use rand::distr::weighted::WeightedIndex;
use rand::prelude::*;
use rand::seq::SliceRandom;

fn main() -> Result<(), Box<dyn Error>> {
    let socket = Arc::new(UdpSocket::bind("0.0.0.0:53")?);
    
    // Cliente HTTPS
    let client = reqwest::blocking::Client::builder()
    .danger_accept_invalid_certs(true)   // acepta el cert self-signed (solo dev)
    .build()?;
    
    // Obtener la URL del DNS API
    let dns_api_url = env::var("DNS_API_URL").unwrap_or_else(|_| "http://host.docker.internal:8080".to_string());

    // %%%%% Loop para recibir las solicitudes
    loop {
        let mut buffer_solicitud = [0u8; 512]; // Buffer para recibir la solicitud del cliente
        let (amt, src) = match socket.recv_from(&mut buffer_solicitud) { // Esperar la solicitud y guardarla en el buffer
            Ok(v) => v,
            Err(e) => { eprintln!("Error en recv_from: {e}"); continue; }
        };

        let datos = buffer_solicitud[..amt].to_vec();

        // Clonar los handles compartidos para este hilo
        let socket = Arc::clone(&socket);
        let client = client.clone();
        let dns_api_url = dns_api_url.clone();

        thread::spawn(move || {
            // Extraer valores de la solicitud enviada por el cliente
            // let id = u16::from_be_bytes([datos[0],datos[1]]); // Extraer id (mas adelante hay que incluirlo en la respuesta)
            let QR: bool = ((datos[2] >> 7) & 1) == 1; // Extraer QR (Booleano)
            let op_code: u8 = (datos[2] >> 3) & 0b0000_1111; // Extraer op_code (Numero entero en binario)

            let mut ip = String::new();

            if !QR && op_code == 0 {
                // identificar el host que se esta tratando de resolver
                let domain = extraer_host(&datos[12..]); // [12..] para saltarnos el header
                
                // consultar a DNS API (GET /api/exists) si existe un registro para este host
                let mut url = format!("{}/api/exists?domain={}", dns_api_url, domain);
                let mut response = match client.get(&url).send() {
                    Ok(r) => r,
                    Err(e) => { eprintln!("Error consultando /api/exists: {e}"); return; }
                };
                let mut body: serde_json::Value = match response.json() {
                    Ok(b) => b,
                    Err(e) => { eprintln!("Error parseando respuesta de /api/exists: {e}"); return; }
                };

                // consultar a DNS API (GET /api/exists) el codigo pais del host
                url = format!("{}/api/ip_country?ip={}", dns_api_url, &src.ip());
                response = match client.get(&url).send() {
                    Ok(r) => r,
                    Err(e) => { eprintln!("Error consultando /api/ip_country: {e}"); return; }
                };
                let respuesta_ip_country: serde_json::Value = match response.json() {
                    Ok(b) => b,
                    Err(e) => { eprintln!("Error parseando respuesta de /api/ip_country: {e}"); return; }
                };

                if body.as_bool() == Some(false) {
                    // Si no existe registro para este dominio entonces codificar
                    // la solicitud y enviarla a POST /api/dns_resolver via HTTPS
                    let codificado = codificar_base64(&datos);

                    println!("No hay registro para este host, resolviendo con DNS externo...");
                    
                    // Resolver dominio
                    let resp = match client.post(format!("{}/api/dns_resolver", dns_api_url))
                        .json(&serde_json::json!({ "data": codificado }))
                        .send() {
                            Ok(r) => r,
                            Err(e) => { eprintln!("Error en /api/dns_resolver: {e}"); return; }
                        };
                    let json: serde_json::Value = match resp.json() {
                        Ok(j) => j,
                        Err(e) => { eprintln!("Error parseando dns_resolver: {e}"); return; }
                    };

                    let data_str = match json["data"].as_str() {
                        Some(s) => s,
                        None => { eprintln!("dns_resolver no devolvió 'data'"); return; }
                    };

                    let respuesta_dns = match decodificar_base64(data_str) {
                        Some(b) => b,
                        None => { eprintln!("base64 inválido en la respuesta"); return; }
                    };

                    if let Err(e) = socket.send_to(&respuesta_dns, &src) {
                        eprintln!("Error enviando la respuesta: {e}");
                    }
                    return;
                } else {
                    // Filtrar los unhealthy
                    if let Some(ips) = body["ips"].as_array_mut() {
                        ips.retain(|e| e["healthy"].as_bool() == Some(true));
                        if ips.is_empty() {
                            eprintln!("No se encontraron IPs saludables para este registro");
                            return;
                        }
                    }
                    let ips = match body["ips"].as_array() {
                        Some(a) => a,
                        None => { eprintln!("Este registro no posee campo ips valido."); return; }
                    };
                    match body["type"].as_str() {
                        Some("single") => {
                            ip = match single_ip(ips) {
                                Some(v) => v,
                                None => { eprintln!("Error obteniendo la IP single"); return; }
                            };
                        }
                        Some("multi") => {
                            let counter = match body["counter"].as_u64() {
                                Some(c) => c,
                                None => { eprintln!("Este registro no posee campo counter valido."); return; }
                            };
                            ip = match multi_ip(ips, counter) {
                                Some(v) => v,
                                None => { eprintln!("Error obteniendo la IP multi"); return; }
                            };
                        }
                        Some("weight") => {
                            ip = match weight_ip(ips) {
                                Some(v) => v,
                                None => { eprintln!("Error obteniendo la IP weight"); return; }
                            };
                        }
                        Some("round-trip") => {
                            let url_lat = format!("{}/api/latency?domain={}", dns_api_url, domain);
                            let resp_lat = match client.get(&url_lat).send() {
                                Ok(r) => r,
                                Err(e) => { eprintln!("Error consultando /api/latency: {e}"); return; }
                            };
                            let latencias: serde_json::Value = match resp_lat.json() {
                                Ok(j) => j,
                                Err(e) => { eprintln!("Error parseando /api/latency: {e}"); return; }
                            };
                            let filas = match latencias.as_array() {
                                Some(a) => a,
                                None => { eprintln!("/api/latency no devolvió una lista"); return; }
                            };
                            // Coordenadas aproximadas del cliente a partir de su país
                            // (None si el país es desconocido -> rtt_ip usa mínimo global)
                            let cliente_coords = respuesta_ip_country["country_code"]
                                .as_str()
                                .and_then(pais_a_coordenadas);
                            ip = match rtt_ip(filas, cliente_coords) {
                                Some(v) => v,
                                None => { eprintln!("No hay IPs sanas con latencia para round-trip"); return; }
                            };
                        }
                        Some("geo") => {
                            let codigo_pais = match respuesta_ip_country["country_code"].as_str() {
                                Some(c) => c,
                                None => { eprintln!("No se pudo obtener el country_code para el registro geo"); return; }
                            };
                            ip = match geo_ip(ips, codigo_pais) {
                                Some(v) => v,
                                None => { eprintln!("Error obteniendo la IP geo"); return; }
                            };
                        }
                        _ => println!("Tipo de registro no soportado."),
                    }
                    println!("Exito! IP: {}", ip);
                }
            } else {
                println!("No estandar query");
            }

            let buffer_respuesta = match construir_respuesta(&datos, &ip) {
                Some(b) => b,
                None => { eprintln!("No se pudo construir la respuesta (IP invalida: '{ip}')"); return; }
            };

            if let Err(e) = socket.send_to(&buffer_respuesta, &src) {
                eprintln!("Error enviando la respuesta: {e}");
            }
        });
    }
}

fn extraer_host(buffer_solicitud: &[u8]) -> String {
    let mut host = String::new();
    let mut longitud_label = buffer_solicitud[0] as usize;
    let mut indice = 0;

    while buffer_solicitud[indice] != 0 {
        let inicio = indice + 1;
        let fin = indice + longitud_label;

        for &byte in &buffer_solicitud[inicio..=fin]{
            host.push(byte as char);
        }

        indice = fin + 1;
        longitud_label = buffer_solicitud[indice] as usize;

        if buffer_solicitud[indice] != 0 {
            host.push('.');
        }
    }
    host
}

fn codificar_base64(buffer: &[u8]) -> String {
    STANDARD.encode(buffer)
}

fn decodificar_base64(texto: &str) -> Option<Vec<u8>> {
    STANDARD.decode(texto).ok()
}

fn construir_respuesta(buffer_solicitud: &[u8], ip: &str) -> Option<Vec<u8>> {
    // Encontrar el final de Question
    let mut longitud_label;
    let mut i = 12;

    while buffer_solicitud[i] != 0 {
        longitud_label = buffer_solicitud[i];
        i = i + longitud_label as usize + 1;
    }

    let i_final_question = i + 5;

    // Copiar el buffer de la consulta
    let mut buffer_respuesta = Vec::new();
    buffer_respuesta.extend_from_slice(&buffer_solicitud[..i_final_question]);
    
    buffer_respuesta[2] |= 0x80; // Cambiar QR a 1 (indicar que es una respuesta)
    buffer_respuesta[7] = 0x1; // Cambiar ANCOUNT (indicar que hay una respuesta)
    
    // NAME de la respuesta. Pasamos un puntero a donde se encuentra el name del query
    buffer_respuesta.push(0xC0); // Indicar que es puntero
    buffer_respuesta.push(0x0C); // Indicar el offset desde el inicio (12)

    // TYPE -> A = 1
    buffer_respuesta.push(0x00); 
    buffer_respuesta.push(0x01);

    // CLASS -> IN = 1
    buffer_respuesta.push(0x00); 
    buffer_respuesta.push(0x01);

    // TTL -> 300 seg
    buffer_respuesta.push(0x00); 
    buffer_respuesta.push(0x00);
    buffer_respuesta.push(0x01); 
    buffer_respuesta.push(0x2c);

    // RDLENGHT -> es IPv4 por lo tanto es 4
    buffer_respuesta.push(0x00); 
    buffer_respuesta.push(0x04);

    // Parsear la ip y pushear los octetos al buffer
    let address: Ipv4Addr = ip.parse().ok()?; // None si la IP no es valida
    let octetos = address.octets();
    buffer_respuesta.extend_from_slice(&octetos);

    Some(buffer_respuesta)
}

// Hay que filtrar las que no son healthy
fn single_ip(ips: &[serde_json::Value]) -> Option<String> {
    Some(ips.first()?["ip"].as_str()?.to_string())
}

fn multi_ip(ips: &[serde_json::Value], counter: u64) -> Option<String> {
    if ips.is_empty() {
        return None; // evita el panic por modulo entre cero
    }
    let index = (counter as usize) % ips.len();
    Some(ips[index]["ip"].as_str()?.to_string())
}

fn weight_ip(ips: &[serde_json::Value]) -> Option<String> {
    let weights: Vec<u64> = ips.iter().map(|e| e["weight"].as_u64().unwrap_or(0)).collect();
    let dist = WeightedIndex::new(&weights).ok()?; // None si estan vacios o todos en cero
    let mut rng = rand::rng();
    ips[dist.sample(&mut rng)]["ip"].as_str().map(|s| s.to_string())
}

fn geo_ip(ips: &[serde_json::Value], codigo_pais : &str) -> Option<String> {
    let ip = ips.iter()
        .find(|e| e["country_code"].as_str() == Some(codigo_pais))
        .and_then(|e| e["ip"].as_str())
        .map(|e| e.to_string());

    if ip.is_none() {
        let mut rng = rand::rng();
        ips.choose(&mut rng)?["ip"].as_str().map(|e| e.to_string())
    } else {
        ip
    }
}

// Distancia en km entre dos coordenadas (haversine). Sirve para medir la
// cercanía entre el cliente y cada health checker
fn haversine_km(lat1: f64, lon1: f64, lat2: f64, lon2: f64) -> f64 {
    let radio = 6371.0_f64;
    let (p1, p2) = (lat1.to_radians(), lat2.to_radians());
    let dphi = (lat2 - lat1).to_radians();
    let dlambda = (lon2 - lon1).to_radians();
    let a = (dphi / 2.0).sin().powi(2) + p1.cos() * p2.cos() * (dlambda / 2.0).sin().powi(2);
    2.0 * radio * a.sqrt().asin()
}

fn rtt_ip(latencias: &[serde_json::Value], cliente: Option<(f64, f64)>) -> Option<String> {
    // Determinar el checker más cercano al cliente
    let checker_cercano: Option<String> = cliente.and_then(|(clat, clon)| {
        latencias.iter()
            .filter_map(|r| {
                let id = r["checker_location_id"].as_str()?;
                let lat = r["checker_latitude"].as_f64()?;
                let lon = r["checker_longitude"].as_f64()?;
                Some((id.to_string(), haversine_km(clat, clon, lat, lon)))
            })
            .min_by(|a, b| a.1.partial_cmp(&b.1).unwrap_or(std::cmp::Ordering::Equal))
            .map(|(id, _)| id)
    });

    // Menor latencia entre las mediciones sanas, opcionalmente restringido a
    // un checker específico.
    let elegir = |restringir: Option<&str>| -> Option<String> {
        latencias.iter()
            .filter(|r| r["is_healthy"].as_bool() == Some(true))
            .filter(|r| match restringir {
                Some(id) => r["checker_location_id"].as_str() == Some(id),
                None => true,
            })
            .filter_map(|r| Some((r["ip"].as_str()?, r["latency_ms"].as_f64()?)))
            .min_by(|a, b| a.1.partial_cmp(&b.1).unwrap_or(std::cmp::Ordering::Equal))
            .map(|(ip, _)| ip.to_string())
    };

    // Primero el checker más cercano; si no da resultado, mínimo global.
    elegir(checker_cercano.as_deref()).or_else(|| elegir(None))
}

// Mapea un código de país ISO-3166 alpha-2 a coordenadas aproximadas (centroide
// del país). Se usa para estimar la ubicación del cliente a partir del país que
// devuelve /api/ip_country. Devuelve None si el país no está en la tabla.
// Generada con Claude Opus 4.8
fn pais_a_coordenadas(codigo: &str) -> Option<(f64, f64)> {
    let coords = match codigo {
        // América
        "CR" => (9.7489, -83.7534),   "US" => (37.0902, -95.7129),
        "CA" => (56.1304, -106.3468), "MX" => (23.6345, -102.5528),
        "GT" => (15.7835, -90.2308),  "PA" => (8.5380, -80.7821),
        "NI" => (12.8654, -85.2072),  "HN" => (15.2000, -86.2419),
        "SV" => (13.7942, -88.8965),  "BZ" => (17.1899, -88.4976),
        "CO" => (4.5709, -74.2973),   "VE" => (6.4238, -66.5897),
        "BR" => (-14.2350, -51.9253), "AR" => (-38.4161, -63.6167),
        "CL" => (-35.6751, -71.5430), "PE" => (-9.1900, -75.0152),
        "EC" => (-1.8312, -78.1834),  "BO" => (-16.2902, -63.5887),
        "UY" => (-32.5228, -55.7658), "PY" => (-23.4425, -58.4438),
        // Europa
        "GB" => (55.3781, -3.4360),   "IE" => (53.1424, -7.6921),
        "FR" => (46.2276, 2.2137),    "ES" => (40.4637, -3.7492),
        "PT" => (39.3999, -8.2245),   "DE" => (51.1657, 10.4515),
        "IT" => (41.8719, 12.5674),   "NL" => (52.1326, 5.2913),
        "BE" => (50.5039, 4.4699),    "CH" => (46.8182, 8.2275),
        "AT" => (47.5162, 14.5501),   "SE" => (60.1282, 18.6435),
        "NO" => (60.4720, 8.4689),    "DK" => (56.2639, 9.5018),
        "FI" => (61.9241, 25.7482),   "PL" => (51.9194, 19.1451),
        "RU" => (61.5240, 105.3188),  "UA" => (48.3794, 31.1656),
        "TR" => (38.9637, 35.2433),
        // Asia / Oceanía / África
        "CN" => (35.8617, 104.1954),  "JP" => (36.2048, 138.2529),
        "KR" => (35.9078, 127.7669),  "IN" => (20.5937, 78.9629),
        "ID" => (-0.7893, 113.9213),  "SG" => (1.3521, 103.8198),
        "TH" => (15.8700, 100.9925),  "VN" => (14.0583, 108.2772),
        "PH" => (12.8797, 121.7740),  "AU" => (-25.2744, 133.7751),
        "NZ" => (-40.9006, 174.8860), "ZA" => (-30.5595, 22.9375),
        "EG" => (26.8206, 30.8025),   "NG" => (9.0820, 8.6753),
        "MA" => (31.7917, -7.0926),   "AE" => (23.4241, 53.8478),
        "SA" => (23.8859, 45.0792),   "IL" => (31.0461, 34.8516),
        _ => return None,
    };
    Some(coords)
}