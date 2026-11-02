package com.sigaa.service;

import com.sigaa.model.NotaNumero;
import com.sigaa.model.Usuario;
import com.sigaa.repository.NotaNumeroRepository;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotaNumeroService {
    public static final String DISPONIBLE = "DISPONIBLE";
    public static final String RESERVADO = "RESERVADO";
    public static final String OCUPADO = "OCUPADO";
    private static final int MINUTOS_RESERVA = 10;
    private static final int VENTANA_NUMEROS = 5;
    private static final ZoneId PANAMA = ZoneId.of("America/Panama");
    private static final Pattern NUMERO = Pattern.compile("^(?:OAT-|N-)?(\\d{1,3})(?:-(\\d{4}))?$", Pattern.CASE_INSENSITIVE);

    private final NotaNumeroRepository repository;
    private final NotaTrazabilidadService trazabilidad;

    public NotaNumeroService(NotaNumeroRepository repository, NotaTrazabilidadService trazabilidad) {
        this.repository = repository;
        this.trazabilidad = trazabilidad;
    }

    @Transactional
    public List<NotaNumero> listar() {
        liberarExpiradas();
        int anio = LocalDate.now(PANAMA).getYear();
        asegurarVentana(anio);
        return repository.findAllByOrderByNumeroAsc().stream()
            .filter(item -> item.getAnio() == anio && item.getConsecutivo() != null).toList();
    }

    @Transactional(readOnly = true)
    public NotaNumero obtener(Long id) {
        return repository.findById(id).orElseThrow(() -> new NoSuchElementException("El número seleccionado no existe."));
    }

    @Transactional
    public void asegurarVentana(int anio) {
        repository.bloquearAnio(anio);
        List<NotaNumero> inventario = repository.findAllForUpdate().stream()
            .filter(item -> item.getAnio() == anio).toList();
        int ocupado = inventario.stream().filter(item -> OCUPADO.equals(item.getEstado()))
            .mapToInt(this::consecutivo).max().orElse(0);
        long disponibles = inventario.stream().filter(item -> DISPONIBLE.equals(item.getEstado())
            && consecutivo(item) > ocupado).count();
        int maximo = inventario.stream().mapToInt(this::consecutivo).max().orElse(0);
        List<NotaNumero> nuevos = new ArrayList<>();
        while (disponibles < VENTANA_NUMEROS && maximo < 999) {
            NotaNumero numero = new NotaNumero();
            numero.setAnio(anio); numero.setConsecutivo(++maximo);
            numero.setNumero(formatear(maximo, anio));
            nuevos.add(numero); disponibles++;
        }
        repository.saveAll(nuevos);
    }

    @Transactional
    public int cargar(List<String> valores) {
        int anio = LocalDate.now(PANAMA).getYear();
        repository.bloquearAnio(anio);
        List<NotaNumero> inventario = repository.findAllForUpdate();
        List<NotaNumero> nuevos = new ArrayList<>();
        for (String valor : valores) {
            var matcher = NUMERO.matcher(valor == null ? "" : valor.trim());
            if (!matcher.matches() || Integer.parseInt(matcher.group(1)) == 0
                    || (matcher.group(2) != null && Integer.parseInt(matcher.group(2)) != anio)) {
                throw new IllegalArgumentException("Indica números del año actual entre 001 y 999.");
            }
            int secuencia = Integer.parseInt(matcher.group(1));
            if (inventario.stream().anyMatch(item -> item.getAnio() == anio && consecutivo(item) == secuencia)
                    || nuevos.stream().anyMatch(item -> item.getConsecutivo() == secuencia)) {
                throw new IllegalArgumentException("El número " + formatear(secuencia, anio) + " ya existe o está repetido.");
            }
            NotaNumero numero = new NotaNumero();
            numero.setAnio(anio); numero.setConsecutivo(secuencia); numero.setNumero(formatear(secuencia, anio));
            nuevos.add(numero);
        }
        repository.saveAll(nuevos);
        trazabilidad.audit("CARGAR_NUMEROS", null, "Se cargaron " + nuevos.size() + " números del año " + anio + ".");
        return nuevos.size();
    }

    public static String formatear(int consecutivo, int anio) {
        if (consecutivo < 1 || consecutivo > 999) throw new IllegalArgumentException("El consecutivo debe estar entre 001 y 999.");
        return String.format(Locale.ROOT, "OAT-%03d-%04d", consecutivo, anio);
    }

    private int consecutivo(NotaNumero numero) {
        if (numero.getConsecutivo() != null) return numero.getConsecutivo();
        var matcher = NUMERO.matcher(numero.getNumero() == null ? "" : numero.getNumero());
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private void exigirAnioActual(NotaNumero numero) {
        if (numero.getAnio() != LocalDate.now(PANAMA).getYear()) {
            throw new IllegalStateException("Selecciona un número del año actual; la reserva corresponde a otro año.");
        }
        if (numero.getConsecutivo() == null) {
            throw new IllegalStateException("Este identificador histórico no corresponde a un consecutivo anual válido. Selecciona otro número.");
        }
    }

    @Transactional
    public NotaNumero reservar(Long id, Usuario usuario) {
        List<NotaNumero> inventario = repository.findAllForUpdate();
        NotaNumero numero = buscar(inventario, id);
        exigirAnioActual(numero);
        LocalDateTime ahora = LocalDateTime.now();
        inventario.forEach(item -> normalizarExpirada(item, ahora));
        if (OCUPADO.equals(numero.getEstado())) {
            throw new IllegalStateException("Este número ya está ocupado.");
        }
        if (RESERVADO.equals(numero.getEstado()) && !esPropietario(numero, usuario)) {
            throw new IllegalStateException("Este número acaba de ser reservado por otra persona.");
        }
        if (!esPropietario(numero, usuario)) exigirPrimerDisponible(inventario, numero);
        numero.setEstado(RESERVADO);
        numero.setReservadoPor(usuario);
        numero.setReservadoDesde(ahora);
        numero.setReservadoHasta(numero.getReservadoDesde().plusMinutes(MINUTOS_RESERVA));
        NotaNumero guardado = repository.save(numero);
        trazabilidad.audit("RESERVAR_NUMERO", null, "Se reservó temporalmente el número " + numero.getNumero() + ".");
        return guardado;
    }

    @Transactional
    public NotaNumero renovar(Long id, Usuario usuario) {
        NotaNumero numero = bloquear(id);
        exigirAnioActual(numero);
        LocalDateTime ahora = LocalDateTime.now();
        if (!RESERVADO.equals(numero.getEstado()) || reservaExpirada(numero, ahora)) {
            normalizarExpirada(numero, ahora);
            throw new IllegalStateException("La reserva venció. Selecciona nuevamente el número.");
        }
        exigirPropietario(numero, usuario);
        numero.setReservadoHasta(ahora.plusMinutes(MINUTOS_RESERVA));
        return repository.save(numero);
    }

    @Transactional
    public void liberar(Long id, Usuario usuario, boolean administrador) {
        NotaNumero numero = bloquear(id);
        LocalDateTime ahora = LocalDateTime.now();
        normalizarExpirada(numero, ahora);
        if (DISPONIBLE.equals(numero.getEstado())) return;
        if (OCUPADO.equals(numero.getEstado())) {
            throw new IllegalStateException("Un número ocupado no puede liberarse.");
        }
        if (!administrador) exigirPropietario(numero, usuario);
        limpiarReserva(numero);
        repository.save(numero);
        trazabilidad.audit("LIBERAR_NUMERO", null, "Se liberó la reserva del número " + numero.getNumero() + ".");
    }

    /** Convierte la reserva en ocupación dentro de la transacción de creación. */
    @Transactional
    public NotaNumero ocuparReserva(Long id, Usuario usuario) {
        return consumirReserva(id, usuario).numero();
    }

    /** Conserva la fecha real de la reserva antes de borrar sus campos al ocuparla. */
    @Transactional
    public ReservaConsumida consumirReserva(Long id, Usuario usuario) {
        List<NotaNumero> inventario = repository.findAllForUpdate();
        NotaNumero numero = buscar(inventario, id);
        exigirAnioActual(numero);
        LocalDateTime ahora = LocalDateTime.now();
        if (!RESERVADO.equals(numero.getEstado()) || reservaExpirada(numero, ahora)) {
            normalizarExpirada(numero, ahora);
            throw new IllegalStateException("La reserva del número venció o ya no está disponible.");
        }
        exigirPropietario(numero, usuario);
        Usuario reservadoPor = numero.getReservadoPor();
        LocalDateTime reservadoDesde = numero.getReservadoDesde();
        numero.setEstado(OCUPADO);
        numero.setReservadoPor(null);
        numero.setReservadoDesde(null);
        numero.setReservadoHasta(null);
        return new ReservaConsumida(repository.save(numero), reservadoPor, reservadoDesde);
    }

    public record ReservaConsumida(NotaNumero numero, Usuario usuario, LocalDateTime desde) {}

    @Transactional
    public int liberarExpiradas() {
        return repository.liberarReservasExpiradas(LocalDateTime.now());
    }

    private NotaNumero bloquear(Long id) {
        return repository.findByIdForUpdate(id)
            .orElseThrow(() -> new NoSuchElementException("El número seleccionado no existe."));
    }

    private NotaNumero buscar(List<NotaNumero> inventario, Long id) {
        return inventario.stream().filter(item -> item.getId().equals(id)).findFirst()
            .orElseThrow(() -> new NoSuchElementException("El número seleccionado no existe."));
    }

    private void exigirPrimerDisponible(List<NotaNumero> inventario, NotaNumero seleccionado) {
        Comparator<NotaNumero> orden = Comparator
            .comparing((NotaNumero item) -> valorNumerico(item.getNumero()))
            .thenComparing(NotaNumero::getNumero, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(NotaNumero::getId);
        inventario = inventario.stream().filter(item -> item.getAnio().equals(seleccionado.getAnio())
            && item.getConsecutivo() != null).toList();
        NotaNumero actual = inventario.stream().filter(item -> OCUPADO.equals(item.getEstado())).max(orden).orElse(null);
        NotaNumero primero = inventario.stream()
            .filter(item -> DISPONIBLE.equals(item.getEstado()))
            .filter(item -> actual == null || orden.compare(item, actual) > 0)
            .min(orden).orElseThrow(() -> new IllegalStateException("No hay números disponibles para registrar la nota."));
        if (!primero.getId().equals(seleccionado.getId())) {
            throw new IllegalStateException("Selecciona el primer número disponible: " + primero.getNumero() + ".");
        }
    }

    private BigInteger valorNumerico(String numero) {
        var matcher = NUMERO.matcher(numero == null ? "" : numero);
        return matcher.matches() ? new BigInteger(matcher.group(1)) : BigInteger.ZERO;
    }

    private void normalizarExpirada(NotaNumero numero, LocalDateTime ahora) {
        if (RESERVADO.equals(numero.getEstado()) && reservaExpirada(numero, ahora)) {
            limpiarReserva(numero);
            repository.save(numero);
        }
    }

    private boolean reservaExpirada(NotaNumero numero, LocalDateTime ahora) {
        return numero.getReservadoHasta() == null || !numero.getReservadoHasta().isAfter(ahora);
    }

    private boolean esPropietario(NotaNumero numero, Usuario usuario) {
        return numero.getReservadoPor() != null && numero.getReservadoPor().getId().equals(usuario.getId());
    }

    private void exigirPropietario(NotaNumero numero, Usuario usuario) {
        if (!esPropietario(numero, usuario)) {
            throw new AccessDeniedException("La reserva pertenece a otro usuario.");
        }
    }

    private void limpiarReserva(NotaNumero numero) {
        numero.setEstado(DISPONIBLE);
        numero.setReservadoPor(null);
        numero.setReservadoDesde(null);
        numero.setReservadoHasta(null);
    }
}
