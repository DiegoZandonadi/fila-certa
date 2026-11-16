package br.com.filacerta.service;

import br.com.filacerta.domain.ModoDeslocamento;
import org.springframework.stereotype.Service;

/** Metrica de localizacao: distancia (Haversine) e tempo de deslocamento ate o estabelecimento. */
@Service
public class DistanciaService {

    private static final double RAIO_TERRA_KM = 6371.0;
    /** Ruas nao sao linha reta: a distancia por via costuma ser ~30% maior. */
    private static final double FATOR_VIA = 1.3;

    public double km(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return RAIO_TERRA_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public int minutosDeslocamento(double km, ModoDeslocamento modo) {
        ModoDeslocamento m = modo == null ? ModoDeslocamento.TRANSPORTE : modo;
        double horas = (km * FATOR_VIA) / m.getKmPorHora();
        return Math.max(1, (int) Math.ceil(horas * 60));
    }
}
