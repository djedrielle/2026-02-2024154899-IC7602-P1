use std::net::UdpSocket;
use std::net::Ipv4Addr;
use std::env;
use std::error::Error;
use base64::Engine;
use base64::engine::general_purpose::STANDARD;
use rand::distr::weighted::WeightedIndex;
use rand::prelude::*;
use rand::seq::SliceRandom;

fn main() -> Result<(), Box<dyn Error>> {
    {
        let socket = UdpSocket::bind("0.0.0.0:53")?;
        
        // Cliente HTTPS
        let client = reqwest::blocking::Client::builder()
        .danger_accept_invalid_certs(true)   // acepta el cert self-signed (solo dev)
        .build()?;
        
        // Obtener la URL del DNS API
        let dns_api_url = env::var("DNS_API_URL").unwrap_or_else(|_| "https://host.docker.internal:8443".to_string());

        let mut buffer_solicitud = [0; 512]; // Buffer para recibir la solicitud del cliente
        let (amt, src) = socket.recv_from(&mut buffer_solicitud)?; // Esperar la solicitud y guardarla en el buffer

        // Extraer valores de la solicitud enviada por el cliente
        // let id = u16::from_be_bytes([buffer_solicitud[0],buffer_solicitud[1]]); // Extraer id (mas adelante hay que incluirlo en la respuesta)
        let QR: bool = ((buffer_solicitud[2] >> 7) & 1) == 1; // Extraer QR (Booleano)
        let op_code: u8 = (buffer_solicitud[2] >> 3) & 0b0000_1111; // Extraer op_code (Numero entero en binario)

        let mut ip = String::new();

        if !QR && op_code == 0 {
            // identificar el host que se esta tratando de resolver
            let domain = extraer_host(&buffer_solicitud[12..]); // [12..] para saltarnos el header
            
            // consultar a DNS API (GET /api/exists) si existe un registro para este host
            let mut url = format!("{}/api/exists?domain={}", dns_api_url, domain);
            let mut response = client.get(&url).send()?;
            let mut body: serde_json::Value = response.json()?;

            println!("Body: {}", body);

            // consultar a DNS API (GET /api/exists) el codigo pais del host
            url = format!("{}/api/ip_country?ip={}", dns_api_url, &src.ip());
            response = client.get(&url).send()?;
            let respuesta_ip_country: serde_json::Value = response.json()?;

            if body.as_bool() == Some(false) {
                // Si no existe registro para este dominio entonces codificar
                // la solicitud y enviarla a POST /api/dns_resolver via HTTPS
                let codificado = codificar_base64(&buffer_solicitud[..amt]);

                println!("No hay registro para este host");
                // enviar via HTTPS a POST /api/dns_resolver
            } else {
                // Filtrar los unhealthy
                if let Some(ips) = body["ips"].as_array_mut() {
                    ips.retain(|e| e["healthy"].as_bool() == Some(true));
                    if ips.is_empty() {
                        return Err("No se encontraron IPs saludables para este registro".into());
                    }
                }
                println!("Body filtrado: {}", body);
                match body["type"].as_str() {
                    Some("single") => ip = single_ip(body["ips"].as_array().ok_or("Este registro no posee campo ips valido.")?),
                    Some("multi") => ip = multi_ip(body["ips"].as_array().ok_or("Este registro no posee campo ips valido.")?, body["counter"].as_u64().ok_or("Este registro no posee campo counter valido.")?),
                    Some("weight") => ip = weight_ip(body["ips"].as_array().ok_or("Este registro no posee campo ips valido.")?),
                    Some("round-trip") => println!("rr"),
                    Some("geo") => ip = geo_ip(body["ips"].as_array().ok_or("Este registro no posee campo ips valido.")?, &respuesta_ip_country["country_code"].as_str().expect("Error pasando el codigo pais a geo_ip()")).ok_or("Error obteniendo la IP geo")?,
                    _ => println!("Tipo de registro no soportado."),
                }
                println!("IP: {}", ip);
            }
        } else {
            println!("No estandar query");
        }

        let buffer_respuesta = construir_respuesta(&buffer_solicitud, &ip);
        
        socket.send_to(&buffer_respuesta, &src);
        
    } // the socket is closed here
    Ok(())
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

// Funcion para decodificar

fn construir_respuesta(buffer_solicitud: &[u8], ip: &str) -> Vec<u8> {
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
    let address: Ipv4Addr = ip.parse().expect("IP invalida");
    let octetos = address.octets();
    buffer_respuesta.extend_from_slice(&octetos);

    buffer_respuesta
}

// Hay que filtrar las que no son healthy
fn single_ip(ips: &[serde_json::Value]) -> String {
    ips[0]["ip"].as_str().expect("Error convirtiendo single ip").to_string()
}

fn multi_ip(ips: &[serde_json::Value], counter: u64) -> String {
    let index = (counter as usize) % ips.len();
    ips[index]["ip"].as_str().expect("Error convirtiendo multi ip").to_string()
}

fn weight_ip(ips: &[serde_json::Value]) -> String {
    let weights: Vec<u64> = ips.iter().map(|e| e["weight"].as_u64().expect("No se asignó weight")).collect();
    let dist = WeightedIndex::new(&weights).expect("Surgió algún error obteniendo los pesos");
    let mut rng = rand::rng();
    ips[dist.sample(&mut rng)]["ip"].as_str().expect("Error convirtiendo weight ip").to_string()
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