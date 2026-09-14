use std::net::UdpSocket;
use std::net::Ipv4Addr;
use std::env;
use std::error::Error;
use base64::Engine;
use base64::engine::general_purpose::STANDARD;

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

        if !QR && op_code == 0 {
            // identificar el host que se esta tratando de resolver
            let domain = extraer_host(&buffer_solicitud[12..]); // [12..] para saltarnos el header
            
            // consultar a DNS API (GET /api/exists) si existe un registro para este host
            let url = format!("{}/api/exists?domain={}", dns_api_url, domain);
            let response = client.get(&url).send()?;
            let body: serde_json::Value = response.json()?;

            if body.as_bool() == Some(false) {
                // Si no existe registro para este dominio entonces codificar
                // la solicitud y enviarla a POST /api/dns_resolver via HTTPS
                let codificado = codificar_base64(&buffer_solicitud[..amt]);

                println!("No hay registro para este host");
                // enviar via HTTPS a POST /api/dns_resolver
            } else {
                println!("Hay registro para este host");
                println!("Body: {}", body);
            }
        } else {
            println!("No estandar query");
        }

        //let buffer_respuesta = construir_respuesta(&buffer_solicitud, "93.184.216.34");
        
        //socket.send_to(&buffer_respuesta, &src);
        
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
    
    println!("buffer_respuesta: {:02x?}", &buffer_respuesta);
    
    buffer_respuesta
}