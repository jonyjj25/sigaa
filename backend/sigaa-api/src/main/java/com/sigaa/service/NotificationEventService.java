package com.sigaa.service;

import com.sigaa.dto.*;
import com.sigaa.model.*;
import com.sigaa.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

@Service
public class NotificationEventService {

    private final NotificationEventRepository eventRepository;
    private final NotificationSettingRepository settingRepository;
    private final NotificationRecipientRepository recipientRepository;
    private final NotificationTemplateRepository templateRepository;
    private final RoleRepository roleRepository;

    public NotificationEventService(NotificationEventRepository eventRepository,
                                    NotificationSettingRepository settingRepository,
                                    NotificationRecipientRepository recipientRepository,
                                    NotificationTemplateRepository templateRepository,
                                    RoleRepository roleRepository) {
        this.eventRepository = eventRepository;
        this.settingRepository = settingRepository;
        this.recipientRepository = recipientRepository;
        this.templateRepository = templateRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationEventResponse> findAll() {
        return eventRepository.findAllByOrderByNombreAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public NotificationEventResponse findById(Long id) {
        NotificationEvent entity = eventRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado con id: " + id));
        return toResponse(entity);
    }

    @Transactional
    public NotificationEventResponse create(NotificationEventRequest request) {
        if (eventRepository.findByCodigo(request.getCodigo()).isPresent()) {
            throw new IllegalArgumentException("Ya existe un evento con el código: " + request.getCodigo());
        }
        NotificationEvent entity = new NotificationEvent();
        entity.setCodigo(request.getCodigo());
        entity.setNombre(request.getNombre());
        entity.setDescripcion(request.getDescripcion());
        entity.setCategoria(request.getCategoria());
        entity.setActivo(request.getActivo() != null ? request.getActivo() : true);
        return toResponse(eventRepository.save(entity));
    }

    @Transactional
    public NotificationEventResponse update(Long id, NotificationEventRequest request) {
        NotificationEvent entity = eventRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado con id: " + id));
        entity.setCodigo(request.getCodigo());
        entity.setNombre(request.getNombre());
        entity.setDescripcion(request.getDescripcion());
        entity.setCategoria(request.getCategoria());
        entity.setActivo(request.getActivo());
        return toResponse(eventRepository.save(entity));
    }

    @Transactional
    public NotificationEventResponse toggleActivo(Long id) {
        NotificationEvent entity = eventRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado con id: " + id));
        entity.setActivo(!entity.getActivo());
        return toResponse(eventRepository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        if (!eventRepository.existsById(id)) {
            throw new IllegalArgumentException("Evento no encontrado con id: " + id);
        }
        settingRepository.deleteByEventoId(id);
        recipientRepository.deleteByEventoId(id);
        eventRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<NotificationSettingResponse> findSettingsByEvento(Long eventoId) {
        eventRepository.findById(eventoId)
            .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado con id: " + eventoId));
        return settingRepository.findByEventoIdOrderByCanalAsc(eventoId).stream()
            .map(this::toSettingResponse).toList();
    }

    @Transactional
    public NotificationSettingResponse createSetting(Long eventoId, NotificationSettingRequest request) {
        NotificationEvent evento = eventRepository.findById(eventoId)
            .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado con id: " + eventoId));

        settingRepository.findByEventoIdAndCanal(eventoId, request.getCanal()).ifPresent(s -> {
            throw new IllegalArgumentException("Ya existe una configuración para el canal " + request.getCanal() + " en este evento");
        });

        NotificationSetting entity = new NotificationSetting();
        entity.setEvento(evento);
        entity.setCanal(request.getCanal());
        entity.setActivo(request.getActivo());
        entity.setPrioridad(request.getPrioridad());
        entity.setFrecuencia(request.getFrecuencia());
        if (request.getHorarioInicio() != null && !request.getHorarioInicio().isEmpty()) {
            entity.setHorarioInicio(LocalTime.parse(request.getHorarioInicio()));
        }
        if (request.getHorarioFin() != null && !request.getHorarioFin().isEmpty()) {
            entity.setHorarioFin(LocalTime.parse(request.getHorarioFin()));
        }
        entity.setRecordatorioActivo(request.getRecordatorioActivo());
        entity.setRecordatorioHoras(request.getRecordatorioHoras());
        entity.setVencimientoHoras(request.getVencimientoHoras());
        entity.setMensajePersonalizado(request.getMensajePersonalizado());
        if (request.getPlantillaId() != null) {
            templateRepository.findById(request.getPlantillaId()).ifPresent(entity::setPlantilla);
        }
        entity.setCreadoPor(getCurrentUser());
        return toSettingResponse(settingRepository.save(entity));
    }

    @Transactional
    public NotificationSettingResponse updateSetting(Long settingId, NotificationSettingRequest request) {
        NotificationSetting entity = settingRepository.findById(settingId)
            .orElseThrow(() -> new IllegalArgumentException("Configuración no encontrada con id: " + settingId));
        entity.setCanal(request.getCanal());
        entity.setActivo(request.getActivo());
        entity.setPrioridad(request.getPrioridad());
        entity.setFrecuencia(request.getFrecuencia());
        if (request.getHorarioInicio() != null && !request.getHorarioInicio().isEmpty()) {
            entity.setHorarioInicio(LocalTime.parse(request.getHorarioInicio()));
        } else {
            entity.setHorarioInicio(null);
        }
        if (request.getHorarioFin() != null && !request.getHorarioFin().isEmpty()) {
            entity.setHorarioFin(LocalTime.parse(request.getHorarioFin()));
        } else {
            entity.setHorarioFin(null);
        }
        entity.setRecordatorioActivo(request.getRecordatorioActivo());
        entity.setRecordatorioHoras(request.getRecordatorioHoras());
        entity.setVencimientoHoras(request.getVencimientoHoras());
        entity.setMensajePersonalizado(request.getMensajePersonalizado());
        entity.setModificadoPor(getCurrentUser());
        if (request.getPlantillaId() != null) {
            templateRepository.findById(request.getPlantillaId()).ifPresent(entity::setPlantilla);
        } else {
            entity.setPlantilla(null);
        }
        return toSettingResponse(settingRepository.save(entity));
    }

    @Transactional
    public void deleteSetting(Long settingId) {
        if (!settingRepository.existsById(settingId)) {
            throw new IllegalArgumentException("Configuración no encontrada con id: " + settingId);
        }
        settingRepository.deleteById(settingId);
    }

    @Transactional(readOnly = true)
    public List<NotificationRecipientResponse> findRecipientsByEvento(Long eventoId) {
        eventRepository.findById(eventoId)
            .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado con id: " + eventoId));
        return recipientRepository.findByEventoIdOrderByRolNombreAsc(eventoId).stream()
            .map(this::toRecipientResponse).toList();
    }

    @Transactional
    public NotificationRecipientResponse createRecipient(Long eventoId, NotificationRecipientRequest request) {
        NotificationEvent evento = eventRepository.findById(eventoId)
            .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado con id: " + eventoId));
        Role rol = roleRepository.findById(request.getRolId())
            .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado con id: " + request.getRolId()));

        if (!recipientRepository.findByEventoIdAndRolId(eventoId, request.getRolId()).isEmpty()) {
            throw new IllegalArgumentException("Ya existe un destinatario para el rol " + rol.getNombre() + " en este evento");
        }

        NotificationRecipient entity = new NotificationRecipient();
        entity.setEvento(evento);
        entity.setRol(rol);
        entity.setActivo(request.getActivo() != null ? request.getActivo() : true);
        return toRecipientResponse(recipientRepository.save(entity));
    }

    @Transactional
    public NotificationRecipientResponse toggleRecipient(Long recipientId) {
        NotificationRecipient entity = recipientRepository.findById(recipientId)
            .orElseThrow(() -> new IllegalArgumentException("Destinatario no encontrado con id: " + recipientId));
        entity.setActivo(!entity.getActivo());
        return toRecipientResponse(recipientRepository.save(entity));
    }

    @Transactional
    public void deleteRecipient(Long recipientId) {
        if (!recipientRepository.existsById(recipientId)) {
            throw new IllegalArgumentException("Destinatario no encontrado con id: " + recipientId);
        }
        recipientRepository.deleteById(recipientId);
    }

    private NotificationEventResponse toResponse(NotificationEvent entity) {
        NotificationEventResponse r = new NotificationEventResponse();
        r.setId(entity.getId());
        r.setCodigo(entity.getCodigo());
        r.setNombre(entity.getNombre());
        r.setDescripcion(entity.getDescripcion());
        r.setCategoria(entity.getCategoria());
        r.setActivo(entity.getActivo());
        r.setSettings(settingRepository.findByEventoIdOrderByCanalAsc(entity.getId()).stream()
            .map(this::toSettingResponse).toList());
        r.setRecipients(recipientRepository.findByEventoIdOrderByRolNombreAsc(entity.getId()).stream()
            .map(this::toRecipientResponse).toList());
        r.setCreatedAt(entity.getCreatedAt());
        r.setUpdatedAt(entity.getUpdatedAt());
        return r;
    }

    private NotificationSettingResponse toSettingResponse(NotificationSetting entity) {
        NotificationSettingResponse r = new NotificationSettingResponse();
        r.setId(entity.getId());
        r.setEventoId(entity.getEvento().getId());
        r.setEventoNombre(entity.getEvento().getNombre());
        r.setEventoCodigo(entity.getEvento().getCodigo());
        r.setCanal(entity.getCanal());
        r.setActivo(entity.getActivo());
        r.setPrioridad(entity.getPrioridad());
        r.setFrecuencia(entity.getFrecuencia());
        r.setHorarioInicio(entity.getHorarioInicio() != null ? entity.getHorarioInicio().toString() : null);
        r.setHorarioFin(entity.getHorarioFin() != null ? entity.getHorarioFin().toString() : null);
        r.setRecordatorioActivo(entity.getRecordatorioActivo());
        r.setRecordatorioHoras(entity.getRecordatorioHoras());
        r.setVencimientoHoras(entity.getVencimientoHoras());
        r.setMensajePersonalizado(entity.getMensajePersonalizado());
        r.setPlantillaId(entity.getPlantilla() != null ? entity.getPlantilla().getId() : null);
        r.setPlantillaNombre(entity.getPlantilla() != null ? entity.getPlantilla().getNombre() : null);
        r.setCreadoPor(entity.getCreadoPor());
        r.setModificadoPor(entity.getModificadoPor());
        r.setCreatedAt(entity.getCreatedAt());
        r.setUpdatedAt(entity.getUpdatedAt());
        return r;
    }

    private NotificationRecipientResponse toRecipientResponse(NotificationRecipient entity) {
        NotificationRecipientResponse r = new NotificationRecipientResponse();
        r.setId(entity.getId());
        r.setEventoId(entity.getEvento().getId());
        r.setEventoNombre(entity.getEvento().getNombre());
        r.setRolId(entity.getRol().getId());
        r.setRolNombre(entity.getRol().getNombre());
        r.setActivo(entity.getActivo());
        r.setCreatedAt(entity.getCreatedAt());
        return r;
    }

    private String getCurrentUser() {
        try {
            return org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getName();
        } catch (Exception e) {
            return "sistema";
        }
    }
}
