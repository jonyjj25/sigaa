package com.sigaa.service;

import com.sigaa.dto.NotificationChannelRequest;
import com.sigaa.dto.NotificationChannelResponse;
import com.sigaa.model.NotificationChannel;
import com.sigaa.repository.NotificationChannelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationChannelService {

    private final NotificationChannelRepository repository;

    public NotificationChannelService(NotificationChannelRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<NotificationChannelResponse> findAll() {
        return repository.findAllByOrderByNombreAsc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public NotificationChannelResponse findById(Long id) {
        NotificationChannel entity = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Canal no encontrado con id: " + id));
        return toResponse(entity);
    }

    @Transactional
    public NotificationChannelResponse create(NotificationChannelRequest request) {
        if (repository.findByNombre(request.getNombre()).isPresent()) {
            throw new IllegalArgumentException("Ya existe un canal con el nombre: " + request.getNombre());
        }
        NotificationChannel entity = new NotificationChannel();
        entity.setNombre(request.getNombre());
        entity.setDescripcion(request.getDescripcion());
        entity.setIcono(request.getIcono());
        entity.setActivo(request.getActivo() != null ? request.getActivo() : true);
        return toResponse(repository.save(entity));
    }

    @Transactional
    public NotificationChannelResponse update(Long id, NotificationChannelRequest request) {
        NotificationChannel entity = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Canal no encontrado con id: " + id));
        entity.setNombre(request.getNombre());
        entity.setDescripcion(request.getDescripcion());
        entity.setIcono(request.getIcono());
        entity.setActivo(request.getActivo());
        return toResponse(repository.save(entity));
    }

    @Transactional
    public NotificationChannelResponse toggleActivo(Long id) {
        NotificationChannel entity = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Canal no encontrado con id: " + id));
        entity.setActivo(!entity.getActivo());
        return toResponse(repository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("Canal no encontrado con id: " + id);
        }
        repository.deleteById(id);
    }

    private NotificationChannelResponse toResponse(NotificationChannel entity) {
        NotificationChannelResponse r = new NotificationChannelResponse();
        r.setId(entity.getId());
        r.setNombre(entity.getNombre());
        r.setDescripcion(entity.getDescripcion());
        r.setIcono(entity.getIcono());
        r.setActivo(entity.getActivo());
        r.setCreatedAt(entity.getCreatedAt());
        r.setUpdatedAt(entity.getUpdatedAt());
        return r;
    }
}
