-- SIGAA: esquema canonico unico.
-- Generado desde PostgreSQL 18 despues de la depuracion V124.
-- Ejecutar una sola vez sobre una base vacia. No reemplaza ni actualiza esquemas existentes.
-- Datos iniciales: catalogos tecnicos de roles, permisos, modulos y seguridad,
-- mas un unico usuario Superadmin con su hash de contrasena vigente.

BEGIN;

DO $schema_guard$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
    ) THEN
        RAISE EXCEPTION 'SIGAA schema installation requires an empty public schema';
    END IF;
END
$schema_guard$;
--
-- PostgreSQL database dump
--


-- Dumped from database version 18.4
-- Dumped by pg_dump version 18.4

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: acta_acuerdos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.acta_acuerdos (
    id bigint NOT NULL,
    acta_id bigint NOT NULL,
    descripcion text NOT NULL,
    responsable character varying(200) DEFAULT ''::character varying,
    fecha_limite date,
    estado character varying(50) DEFAULT 'Pendiente'::character varying,
    CONSTRAINT acta_acuerdos_estado_check CHECK (((estado)::text = ANY (ARRAY[('Pendiente'::character varying)::text, ('En Progreso'::character varying)::text, ('Completado'::character varying)::text])))
);


--
-- Name: acta_acuerdos_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.acta_acuerdos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: acta_acuerdos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.acta_acuerdos_id_seq OWNED BY public.acta_acuerdos.id;


--
-- Name: acta_adjuntos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.acta_adjuntos (
    id bigint NOT NULL,
    acta_id bigint NOT NULL,
    nombre_original character varying(500) NOT NULL,
    nombre_archivo character varying(500) NOT NULL,
    tipo_mime character varying(100) DEFAULT ''::character varying,
    tamano bigint DEFAULT 0,
    ruta character varying(1000) DEFAULT ''::character varying
);


--
-- Name: acta_adjuntos_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.acta_adjuntos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: acta_adjuntos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.acta_adjuntos_id_seq OWNED BY public.acta_adjuntos.id;


--
-- Name: acta_firmas; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.acta_firmas (
    id bigint NOT NULL,
    acta_id bigint NOT NULL,
    nombre character varying(200) NOT NULL,
    rol character varying(200) DEFAULT ''::character varying,
    fecha timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


--
-- Name: acta_firmas_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.acta_firmas_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: acta_firmas_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.acta_firmas_id_seq OWNED BY public.acta_firmas.id;


--
-- Name: acta_participantes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.acta_participantes (
    id bigint NOT NULL,
    acta_id bigint NOT NULL,
    nombre character varying(200) NOT NULL,
    apellido character varying(200) DEFAULT ''::character varying NOT NULL,
    area character varying(200) DEFAULT ''::character varying,
    asistio boolean DEFAULT false,
    correo character varying(150) DEFAULT ''::character varying,
    institucion character varying(200) DEFAULT ''::character varying,
    cargo character varying(150) DEFAULT ''::character varying
);


--
-- Name: acta_participantes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.acta_participantes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: acta_participantes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.acta_participantes_id_seq OWNED BY public.acta_participantes.id;


--
-- Name: actas; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.actas (
    id bigint NOT NULL,
    codigo character varying(50) NOT NULL,
    fecha date DEFAULT CURRENT_DATE NOT NULL,
    hora time without time zone DEFAULT CURRENT_TIME NOT NULL,
    lugar character varying(200) NOT NULL,
    titulo character varying(300) NOT NULL,
    tipo character varying(50) NOT NULL,
    estado character varying(50) DEFAULT 'Borrador'::character varying NOT NULL,
    responsable character varying(200) NOT NULL,
    objetivos text DEFAULT ''::text,
    temas text DEFAULT '[]'::text,
    acuerdos text DEFAULT '[]'::text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    sesion_id bigint,
    institucion_participantes character varying(300) DEFAULT ''::character varying NOT NULL,
    CONSTRAINT actas_estado_check CHECK (((estado)::text = ANY (ARRAY[('Firmada'::character varying)::text, ('Pendiente Firma'::character varying)::text, ('Borrador'::character varying)::text, ('Terminada'::character varying)::text]))),
    CONSTRAINT actas_tipo_check CHECK (((tipo)::text = ANY (ARRAY[('Ordinaria'::character varying)::text, ('Extraordinaria'::character varying)::text, ('Comité'::character varying)::text, ('Consejo'::character varying)::text])))
);


--
-- Name: actas_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.actas_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: actas_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.actas_id_seq OWNED BY public.actas.id;


--
-- Name: admin_password_reset_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admin_password_reset_requests (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    status character varying(20) DEFAULT 'PENDIENTE'::character varying NOT NULL,
    request_ip character varying(45),
    requested_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    resolved_at timestamp without time zone,
    resolved_by bigint,
    rejection_reason character varying(500),
    temporary_password_expires_at timestamp without time zone,
    CONSTRAINT ck_admin_password_reset_status CHECK (((status)::text = ANY (ARRAY[('PENDIENTE'::character varying)::text, ('APROBADA'::character varying)::text, ('RECHAZADA'::character varying)::text])))
);


--
-- Name: admin_password_reset_requests_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.admin_password_reset_requests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: admin_password_reset_requests_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.admin_password_reset_requests_id_seq OWNED BY public.admin_password_reset_requests.id;


--
-- Name: allowed_http_headers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.allowed_http_headers (
    id bigint NOT NULL,
    header character varying(100) NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: allowed_http_headers_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.allowed_http_headers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: allowed_http_headers_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.allowed_http_headers_id_seq OWNED BY public.allowed_http_headers.id;


--
-- Name: allowed_http_methods; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.allowed_http_methods (
    id bigint NOT NULL,
    metodo character varying(20) NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: allowed_http_methods_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.allowed_http_methods_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: allowed_http_methods_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.allowed_http_methods_id_seq OWNED BY public.allowed_http_methods.id;


--
-- Name: allowed_origins; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.allowed_origins (
    id bigint NOT NULL,
    origen character varying(255) NOT NULL,
    descripcion character varying(255),
    activo boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: allowed_origins_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.allowed_origins_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: allowed_origins_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.allowed_origins_id_seq OWNED BY public.allowed_origins.id;


--
-- Name: asistencia_invitados; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.asistencia_invitados (
    id bigint NOT NULL,
    sesion_id bigint NOT NULL,
    nombre character varying(200) NOT NULL,
    correo character varying(150) NOT NULL,
    telefono character varying(30) DEFAULT ''::character varying,
    institucion character varying(200) DEFAULT ''::character varying,
    cargo character varying(150) DEFAULT ''::character varying,
    acepta_terminos boolean DEFAULT true,
    hora_registro timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: asistencia_invitados_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.asistencia_invitados_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: asistencia_invitados_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.asistencia_invitados_id_seq OWNED BY public.asistencia_invitados.id;


--
-- Name: asistencia_registros; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.asistencia_registros (
    id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    fecha date DEFAULT CURRENT_DATE NOT NULL,
    hora_entrada time without time zone NOT NULL,
    hora_salida time without time zone,
    lat_entrada double precision DEFAULT 0 NOT NULL,
    lng_entrada double precision DEFAULT 0 NOT NULL,
    lat_salida double precision,
    lng_salida double precision,
    sede_id bigint,
    metodo character varying(10) DEFAULT 'GPS'::character varying NOT NULL,
    estado character varying(20) DEFAULT 'presente'::character varying NOT NULL,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    sesion_id bigint
);


--
-- Name: asistencia_registros_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.asistencia_registros_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: asistencia_registros_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.asistencia_registros_id_seq OWNED BY public.asistencia_registros.id;


--
-- Name: asistencia_sesiones; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.asistencia_sesiones (
    id bigint NOT NULL,
    codigo character varying(50) NOT NULL,
    nombre character varying(200) NOT NULL,
    descripcion text DEFAULT ''::text,
    fecha date DEFAULT CURRENT_DATE NOT NULL,
    hora_inicio time without time zone,
    hora_fin time without time zone,
    tipo character varying(30) DEFAULT 'reunion'::character varying NOT NULL,
    creado_por bigint,
    activo boolean DEFAULT true NOT NULL,
    qr_expira_en timestamp without time zone,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    archivado boolean DEFAULT false NOT NULL,
    archivado_en timestamp without time zone
);


--
-- Name: asistencia_sesiones_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.asistencia_sesiones_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: asistencia_sesiones_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.asistencia_sesiones_id_seq OWNED BY public.asistencia_sesiones.id;


--
-- Name: audit_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_logs (
    id bigint NOT NULL,
    usuario character varying(255) NOT NULL,
    accion character varying(100) NOT NULL,
    entidad character varying(100) NOT NULL,
    detalle text,
    direccion_ip character varying(45),
    fecha timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    entidad_id bigint
);


--
-- Name: audit_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.audit_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: audit_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.audit_logs_id_seq OWNED BY public.audit_logs.id;


--
-- Name: backups; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.backups (
    id bigint NOT NULL,
    nombre character varying(255) NOT NULL,
    tamanio character varying(20),
    estado character varying(20) DEFAULT 'completado'::character varying NOT NULL,
    tipo character varying(20) DEFAULT 'automatico'::character varying NOT NULL,
    fecha timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    ruta_archivo character varying(500),
    copia_externa boolean DEFAULT false NOT NULL,
    checksum_sha256 character varying(64),
    verificacion_estado character varying(20) DEFAULT 'pendiente'::character varying NOT NULL,
    verificado_at timestamp without time zone
);


--
-- Name: backups_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.backups_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: backups_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.backups_id_seq OWNED BY public.backups.id;


--
-- Name: carpeta_documentos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.carpeta_documentos (
    id bigint NOT NULL,
    carpeta_id bigint NOT NULL,
    nombre character varying(300) NOT NULL,
    tipo character varying(100) DEFAULT ''::character varying,
    fecha date DEFAULT CURRENT_DATE
);


--
-- Name: carpeta_documentos_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.carpeta_documentos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: carpeta_documentos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.carpeta_documentos_id_seq OWNED BY public.carpeta_documentos.id;


--
-- Name: carpeta_usuarios; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.carpeta_usuarios (
    id bigint NOT NULL,
    carpeta_id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: carpeta_usuarios_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.carpeta_usuarios_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: carpeta_usuarios_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.carpeta_usuarios_id_seq OWNED BY public.carpeta_usuarios.id;


--
-- Name: carpetas; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.carpetas (
    id bigint NOT NULL,
    nombre character varying(300) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    propietario_id bigint,
    tipo character varying(20) DEFAULT 'PERSONAL'::character varying NOT NULL
);


--
-- Name: carpetas_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.carpetas_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: carpetas_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.carpetas_id_seq OWNED BY public.carpetas.id;


--
-- Name: documento_solicitudes_aprobacion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.documento_solicitudes_aprobacion (
    id bigint NOT NULL,
    documento_id bigint NOT NULL,
    solicitante_id bigint NOT NULL,
    aprobador_id bigint NOT NULL,
    estado character varying(20) DEFAULT 'PENDIENTE'::character varying NOT NULL,
    fecha_solicitud timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    comentario_solicitud text DEFAULT ''::text,
    respondido_por_id bigint,
    fecha_respuesta timestamp without time zone,
    comentario_respuesta text DEFAULT ''::text,
    version_id bigint
);


--
-- Name: documento_solicitudes_aprobacion_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.documento_solicitudes_aprobacion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: documento_solicitudes_aprobacion_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.documento_solicitudes_aprobacion_id_seq OWNED BY public.documento_solicitudes_aprobacion.id;


--
-- Name: documento_usuarios; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.documento_usuarios (
    id bigint NOT NULL,
    documento_id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    permiso character varying(20) DEFAULT 'LECTURA'::character varying NOT NULL,
    expira_en timestamp without time zone,
    creado_por_id bigint NOT NULL,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT documento_usuarios_permiso_check CHECK (((permiso)::text = ANY (ARRAY[('LECTURA'::character varying)::text, ('EDICION'::character varying)::text])))
);


--
-- Name: documento_usuarios_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.documento_usuarios_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: documento_usuarios_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.documento_usuarios_id_seq OWNED BY public.documento_usuarios.id;


--
-- Name: documento_versiones; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.documento_versiones (
    id bigint NOT NULL,
    documento_id bigint NOT NULL,
    version character varying(50) NOT NULL,
    autor character varying(200) DEFAULT ''::character varying,
    fecha date DEFAULT CURRENT_DATE,
    cambios text DEFAULT ''::text,
    numero integer,
    archivo_ruta character varying(500),
    archivo_nombre character varying(255),
    contenido_hash character varying(64),
    tamano_bytes bigint,
    paginas integer,
    autor_id bigint,
    registrada_en timestamp without time zone,
    linea_base boolean DEFAULT false NOT NULL,
    CONSTRAINT ck_documento_version_numero CHECK (((numero IS NULL) OR (numero > 0)))
);


--
-- Name: documento_versiones_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.documento_versiones_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: documento_versiones_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.documento_versiones_id_seq OWNED BY public.documento_versiones.id;


--
-- Name: documentos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.documentos (
    id bigint NOT NULL,
    nombre character varying(300) NOT NULL,
    categoria character varying(200) DEFAULT ''::character varying,
    area character varying(200) DEFAULT ''::character varying,
    version character varying(50) DEFAULT '1.0'::character varying,
    fecha date DEFAULT CURRENT_DATE,
    propietario character varying(200) DEFAULT ''::character varying,
    estado character varying(50) DEFAULT 'borrador'::character varying,
    descripcion text DEFAULT ''::text,
    paginas integer DEFAULT 0,
    tamano character varying(50) DEFAULT ''::character varying,
    compartido boolean DEFAULT false,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    archivo_ruta character varying(500),
    permiso character varying(50) DEFAULT 'solo_lectura'::character varying,
    carpeta_id bigint,
    aprobador_id bigint,
    solicitado_por_id bigint,
    lock_version bigint DEFAULT 0 NOT NULL,
    propietario_id bigint,
    clasificacion character varying(30) DEFAULT 'USO_INTERNO'::character varying NOT NULL,
    creacion_id uuid,
    version_actual_id bigint,
    eliminado boolean DEFAULT false NOT NULL,
    CONSTRAINT documentos_clasificacion_check CHECK (((clasificacion)::text = ANY (ARRAY[('PUBLICO'::character varying)::text, ('USO_INTERNO'::character varying)::text, ('RESTRINGIDO'::character varying)::text, ('CONFIDENCIAL'::character varying)::text, ('DATOS_SENSIBLES'::character varying)::text]))),
    CONSTRAINT documentos_estado_check CHECK (((estado)::text = ANY (ARRAY[('aprobado'::character varying)::text, ('en_revision'::character varying)::text, ('borrador'::character varying)::text, ('rechazado'::character varying)::text])))
);


--
-- Name: documentos_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.documentos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: documentos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.documentos_id_seq OWNED BY public.documentos.id;


--
-- Name: grupo_mensajes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.grupo_mensajes (
    id bigint NOT NULL,
    grupo_id bigint NOT NULL,
    autor_id bigint NOT NULL,
    contenido character varying(2000) NOT NULL,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    eliminado boolean DEFAULT false NOT NULL
);


--
-- Name: grupo_mensajes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.grupo_mensajes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: grupo_mensajes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.grupo_mensajes_id_seq OWNED BY public.grupo_mensajes.id;


--
-- Name: grupo_permisos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.grupo_permisos (
    id bigint NOT NULL,
    grupo_id bigint NOT NULL,
    permiso_id bigint NOT NULL
);


--
-- Name: grupo_permisos_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.grupo_permisos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: grupo_permisos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.grupo_permisos_id_seq OWNED BY public.grupo_permisos.id;


--
-- Name: grupo_usuarios; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.grupo_usuarios (
    id bigint NOT NULL,
    grupo_id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    es_responsable boolean DEFAULT false NOT NULL,
    fecha_asignacion timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    activo boolean DEFAULT true NOT NULL
);


--
-- Name: grupo_usuarios_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.grupo_usuarios_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: grupo_usuarios_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.grupo_usuarios_id_seq OWNED BY public.grupo_usuarios.id;


--
-- Name: grupos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.grupos (
    id bigint NOT NULL,
    nombre character varying(150) NOT NULL,
    descripcion character varying(1000),
    unidad_id bigint NOT NULL,
    tipo character varying(80) NOT NULL,
    icono character varying(80),
    color character varying(20),
    activo boolean DEFAULT true NOT NULL,
    creado_por_id bigint,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    actualizado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: grupos_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.grupos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: grupos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.grupos_id_seq OWNED BY public.grupos.id;


--
-- Name: horarios; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.horarios (
    id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    dia integer NOT NULL,
    entrada character varying(5) DEFAULT '08:00'::character varying NOT NULL,
    salida character varying(5) DEFAULT '17:00'::character varying NOT NULL,
    tolerancia integer DEFAULT 15 NOT NULL,
    estado character varying(20) DEFAULT 'normal'::character varying NOT NULL,
    fecha_inicio date,
    fecha_fin date,
    CONSTRAINT horarios_dia_check CHECK (((dia >= 1) AND (dia <= 7)))
);


--
-- Name: horarios_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.horarios_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: horarios_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.horarios_id_seq OWNED BY public.horarios.id;


--
-- Name: inventario_activos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inventario_activos (
    id bigint NOT NULL,
    codigo character varying(50) NOT NULL,
    nombre character varying(300) NOT NULL,
    categoria character varying(200) DEFAULT ''::character varying,
    ubicacion character varying(200) DEFAULT ''::character varying,
    estado character varying(50) DEFAULT 'en buen estado'::character varying,
    fecha_adquisicion date DEFAULT CURRENT_DATE,
    marca_modelo character varying(200) DEFAULT ''::character varying,
    serie character varying(200) DEFAULT ''::character varying,
    serie_institucional character varying(200) DEFAULT ''::character varying,
    responsable character varying(452) DEFAULT ''::character varying,
    proveedor character varying(200) DEFAULT ''::character varying,
    descripcion text DEFAULT ''::text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    lock_version bigint DEFAULT 0 NOT NULL,
    unidad character varying(200) DEFAULT ''::character varying NOT NULL,
    asignado_usuario_id bigint,
    asignado_unidad_id bigint,
    CONSTRAINT inventario_activos_estado_check CHECK (((estado)::text = ANY (ARRAY[('en buen estado'::character varying)::text, ('mantenimiento'::character varying)::text, ('fuera de servicio'::character varying)::text, ('dañado'::character varying)::text]))),
    CONSTRAINT inventario_asignacion_exclusiva CHECK (((asignado_usuario_id IS NULL) OR (asignado_unidad_id IS NULL)))
);


--
-- Name: inventario_activos_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.inventario_activos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: inventario_activos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.inventario_activos_id_seq OWNED BY public.inventario_activos.id;


--
-- Name: ip_restrictions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ip_restrictions (
    id bigint NOT NULL,
    tipo character varying(20) NOT NULL,
    direccion_ip character varying(45) NOT NULL,
    rango_inicio character varying(45),
    rango_fin character varying(45),
    descripcion character varying(255),
    activo boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ip_restrictions_tipo_check CHECK (((tipo)::text = ANY (ARRAY[('WHITELIST'::character varying)::text, ('BLACKLIST'::character varying)::text])))
);


--
-- Name: ip_restrictions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ip_restrictions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ip_restrictions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ip_restrictions_id_seq OWNED BY public.ip_restrictions.id;


--
-- Name: memo_historial; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.memo_historial (
    id bigint NOT NULL,
    memo_id bigint NOT NULL,
    usuario_id bigint,
    accion character varying(80) NOT NULL,
    estado_anterior character varying(30),
    estado_nuevo character varying(30),
    detalle text DEFAULT ''::text NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: memo_historial_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.memo_historial_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: memo_historial_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.memo_historial_id_seq OWNED BY public.memo_historial.id;


--
-- Name: memo_numeros; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.memo_numeros (
    id bigint NOT NULL,
    numero character varying(50) NOT NULL,
    estado character varying(20) DEFAULT 'DISPONIBLE'::character varying NOT NULL,
    reservado_por_id bigint,
    reservado_desde timestamp without time zone,
    reservado_hasta timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    lock_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT memo_numeros_estado_check CHECK (((estado)::text = ANY (ARRAY[('DISPONIBLE'::character varying)::text, ('RESERVADO'::character varying)::text, ('OCUPADO'::character varying)::text]))),
    CONSTRAINT memo_numeros_reserva_check CHECK (((((estado)::text = 'RESERVADO'::text) AND (reservado_por_id IS NOT NULL) AND (reservado_desde IS NOT NULL) AND (reservado_hasta IS NOT NULL) AND (reservado_hasta > reservado_desde)) OR (((estado)::text <> 'RESERVADO'::text) AND (reservado_por_id IS NULL) AND (reservado_desde IS NULL) AND (reservado_hasta IS NULL))))
);


--
-- Name: memo_numeros_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.memo_numeros_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: memo_numeros_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.memo_numeros_id_seq OWNED BY public.memo_numeros.id;


--
-- Name: memos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.memos (
    id bigint NOT NULL,
    numero_id bigint NOT NULL,
    asunto character varying(200) NOT NULL,
    destino character varying(300) NOT NULL,
    descripcion text DEFAULT ''::text NOT NULL,
    creado_por_id bigint NOT NULL,
    seguimiento_usuario_id bigint NOT NULL,
    estado character varying(30) DEFAULT 'EN_SEGUIMIENTO'::character varying NOT NULL,
    completado_por_id bigint,
    completado_at timestamp without time zone,
    anulado_por_id bigint,
    anulado_at timestamp without time zone,
    anulacion_motivo character varying(1000),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    lock_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT memos_anulacion_check CHECK ((((estado)::text <> 'ANULADO'::text) OR ((anulado_por_id IS NOT NULL) AND (anulado_at IS NOT NULL) AND (anulacion_motivo IS NOT NULL) AND (length(TRIM(BOTH FROM anulacion_motivo)) >= 5)))),
    CONSTRAINT memos_completado_check CHECK ((((estado)::text <> 'COMPLETADO'::text) OR ((completado_por_id IS NOT NULL) AND (completado_at IS NOT NULL)))),
    CONSTRAINT memos_estado_check CHECK (((estado)::text = ANY (ARRAY[('EN_SEGUIMIENTO'::character varying)::text, ('COMPLETADO'::character varying)::text, ('ANULADO'::character varying)::text])))
);


--
-- Name: memos_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.memos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: memos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.memos_id_seq OWNED BY public.memos.id;


--
-- Name: modulos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.modulos (
    id bigint NOT NULL,
    nombre character varying(100) NOT NULL,
    ruta character varying(200) NOT NULL,
    icono character varying(100),
    activo boolean DEFAULT true NOT NULL,
    orden integer DEFAULT 0 NOT NULL,
    codigo character varying(50),
    descripcion character varying(500),
    categoria character varying(50) DEFAULT 'principal'::character varying,
    color character varying(20),
    padre_id bigint,
    ruta_api character varying(200),
    tipo_modulo character varying(30) DEFAULT 'MODULO'::character varying,
    nivel_jerarquico integer DEFAULT 0,
    breadcrumb character varying(500),
    pagina_inicio character varying(200),
    url_ayuda character varying(500),
    etiqueta character varying(50),
    componente_angular character varying(200),
    visible_en_menu boolean DEFAULT true,
    visible_en_dashboard boolean DEFAULT true,
    permite_acceso_directo boolean DEFAULT false,
    requiere_autenticacion boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    CONSTRAINT ck_modulos_codigo_tecnico CHECK (((codigo IS NULL) OR ((codigo)::text ~ '^[a-z0-9][a-z0-9_-]*$'::text))),
    CONSTRAINT ck_modulos_orden_positivo CHECK ((orden > 0)),
    CONSTRAINT ck_modulos_ruta_interna CHECK (((ruta)::text ~ '^/[a-z0-9][a-z0-9/_-]*$'::text))
);


--
-- Name: modulos_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.modulos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: modulos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.modulos_id_seq OWNED BY public.modulos.id;


--
-- Name: nota_historial; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.nota_historial (
    id bigint NOT NULL,
    nota_id bigint NOT NULL,
    usuario_id bigint,
    accion character varying(80) NOT NULL,
    estado_anterior character varying(30),
    estado_nuevo character varying(30),
    detalle text DEFAULT ''::text NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: nota_historial_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.nota_historial_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: nota_historial_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.nota_historial_id_seq OWNED BY public.nota_historial.id;


--
-- Name: nota_numeros; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.nota_numeros (
    id bigint NOT NULL,
    numero character varying(50) NOT NULL,
    estado character varying(20) DEFAULT 'DISPONIBLE'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    lock_version bigint DEFAULT 0 NOT NULL,
    reservado_por_id bigint,
    reservado_desde timestamp without time zone,
    reservado_hasta timestamp without time zone,
    anio integer NOT NULL,
    consecutivo integer,
    CONSTRAINT chk_nota_consecutivo CHECK (((consecutivo >= 1) AND (consecutivo <= 999))),
    CONSTRAINT ck_nota_numero_reserva_completa CHECK (((((estado)::text = 'RESERVADO'::text) AND (reservado_por_id IS NOT NULL) AND (reservado_desde IS NOT NULL) AND (reservado_hasta IS NOT NULL) AND (reservado_hasta > reservado_desde)) OR (((estado)::text <> 'RESERVADO'::text) AND (reservado_por_id IS NULL) AND (reservado_desde IS NULL) AND (reservado_hasta IS NULL)))),
    CONSTRAINT nota_numeros_estado_check CHECK (((estado)::text = ANY (ARRAY[('DISPONIBLE'::character varying)::text, ('RESERVADO'::character varying)::text, ('OCUPADO'::character varying)::text])))
);


--
-- Name: nota_numeros_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.nota_numeros_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: nota_numeros_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.nota_numeros_id_seq OWNED BY public.nota_numeros.id;


--
-- Name: nota_pdfs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.nota_pdfs (
    id bigint NOT NULL,
    nota_id bigint NOT NULL,
    numero_version integer NOT NULL,
    nombre_original character varying(300) NOT NULL,
    ruta_interna character varying(1000) NOT NULL,
    tipo_mime character varying(100) DEFAULT 'application/pdf'::character varying NOT NULL,
    tamano_bytes bigint NOT NULL,
    usuario_id bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    vigente boolean DEFAULT true NOT NULL,
    eliminado_por_id bigint,
    eliminado_at timestamp without time zone,
    eliminacion_motivo character varying(1000),
    CONSTRAINT nota_pdfs_eliminacion_completa_check CHECK ((((eliminado_at IS NULL) AND (eliminado_por_id IS NULL) AND (eliminacion_motivo IS NULL)) OR ((eliminado_at IS NOT NULL) AND (eliminado_por_id IS NOT NULL) AND (eliminacion_motivo IS NOT NULL) AND (length(TRIM(BOTH FROM eliminacion_motivo)) >= 5) AND (vigente = false)))),
    CONSTRAINT nota_pdfs_numero_version_check CHECK ((numero_version > 0)),
    CONSTRAINT nota_pdfs_tamano_bytes_check CHECK ((tamano_bytes >= 0))
);


--
-- Name: nota_pdfs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.nota_pdfs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: nota_pdfs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.nota_pdfs_id_seq OWNED BY public.nota_pdfs.id;


--
-- Name: nota_relaciones; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.nota_relaciones (
    id bigint NOT NULL,
    nota_origen_id bigint NOT NULL,
    nota_destino_id bigint NOT NULL,
    tipo character varying(30) NOT NULL,
    creado_por_id bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_nota_relacion_distinta CHECK ((nota_origen_id <> nota_destino_id)),
    CONSTRAINT ck_nota_relacion_tipo CHECK (((tipo)::text = ANY (ARRAY[('RELACIONADA_CON'::character varying)::text, ('RESPONDE_A'::character varying)::text, ('ANTECEDENTE_DE'::character varying)::text])))
);


--
-- Name: nota_relaciones_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.nota_relaciones_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: nota_relaciones_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.nota_relaciones_id_seq OWNED BY public.nota_relaciones.id;


--
-- Name: notas; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notas (
    id bigint NOT NULL,
    numero_id bigint NOT NULL,
    asunto character varying(200) NOT NULL,
    destino character varying(300) NOT NULL,
    descripcion text DEFAULT ''::text NOT NULL,
    creado_por_id bigint NOT NULL,
    seguimiento_usuario_id bigint NOT NULL,
    estado character varying(30) DEFAULT 'EN_SEGUIMIENTO'::character varying NOT NULL,
    pdf_nombre character varying(300),
    pdf_ruta character varying(1000),
    pdf_tamano_bytes bigint,
    firmada_por_id bigint,
    firmada_at timestamp without time zone,
    cerrada_por_id bigint,
    cerrada_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    lock_version bigint DEFAULT 0 NOT NULL,
    sensitiva boolean DEFAULT false NOT NULL,
    anulada_por_id bigint,
    anulada_at timestamp without time zone,
    anulacion_motivo character varying(1000),
    fecha_oficial date NOT NULL,
    destinatario_nombre character varying(200) DEFAULT ''::character varying NOT NULL,
    destinatario_cargo character varying(200) DEFAULT ''::character varying NOT NULL,
    institucion_id bigint,
    contenido text DEFAULT ''::text NOT NULL,
    oficializada_por_id bigint,
    oficializada_at timestamp without time zone,
    firmante_id bigint,
    firmante_nombre character varying(350),
    firmante_cargo character varying(150),
    firmante_institucion character varying(200),
    departamento_id bigint,
    departamento_nombre character varying(200),
    CONSTRAINT ck_notas_departamento_institucion CHECK (((departamento_id IS NULL) OR (institucion_id IS NOT NULL))),
    CONSTRAINT notas_anulacion_completa_check CHECK ((((estado)::text <> 'ANULADA'::text) OR ((anulada_por_id IS NOT NULL) AND (anulada_at IS NOT NULL) AND (anulacion_motivo IS NOT NULL) AND (length(TRIM(BOTH FROM anulacion_motivo)) >= 5)))),
    CONSTRAINT notas_check CHECK (((((estado)::text <> 'FIRMADA'::text) AND ((estado)::text <> 'CERRADA'::text)) OR ((firmada_por_id IS NOT NULL) AND (firmada_at IS NOT NULL)))),
    CONSTRAINT notas_check1 CHECK ((((estado)::text <> 'CERRADA'::text) OR ((cerrada_por_id IS NOT NULL) AND (cerrada_at IS NOT NULL)))),
    CONSTRAINT notas_estado_check CHECK (((estado)::text = ANY (ARRAY[('BORRADOR'::character varying)::text, ('EN_SEGUIMIENTO'::character varying)::text, ('FIRMADA'::character varying)::text, ('CERRADA'::character varying)::text, ('ANULADA'::character varying)::text]))),
    CONSTRAINT notas_pdf_tamano_bytes_check CHECK ((pdf_tamano_bytes >= 0))
);


--
-- Name: notas_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notas_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notas_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.notas_id_seq OWNED BY public.notas.id;


--
-- Name: notificaciones; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notificaciones (
    id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    titulo character varying(300) NOT NULL,
    mensaje text DEFAULT ''::text NOT NULL,
    leida boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: notificaciones_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notificaciones_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notificaciones_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.notificaciones_id_seq OWNED BY public.notificaciones.id;


--
-- Name: notification_channels; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_channels (
    id bigint NOT NULL,
    nombre character varying(50) NOT NULL,
    descripcion character varying(200) DEFAULT ''::character varying NOT NULL,
    icono character varying(50) DEFAULT 'notifications-outline'::character varying NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: notification_channels_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notification_channels_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notification_channels_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.notification_channels_id_seq OWNED BY public.notification_channels.id;


--
-- Name: notification_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_events (
    id bigint NOT NULL,
    codigo character varying(80) NOT NULL,
    nombre character varying(100) NOT NULL,
    descripcion character varying(300) DEFAULT ''::character varying NOT NULL,
    categoria character varying(50) DEFAULT 'general'::character varying NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: notification_events_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notification_events_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notification_events_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.notification_events_id_seq OWNED BY public.notification_events.id;


--
-- Name: notification_global_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_global_settings (
    id bigint NOT NULL,
    clave character varying(80) NOT NULL,
    valor text DEFAULT ''::text NOT NULL,
    descripcion character varying(200) DEFAULT ''::character varying,
    updated_at timestamp without time zone DEFAULT now()
);


--
-- Name: notification_global_settings_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notification_global_settings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notification_global_settings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.notification_global_settings_id_seq OWNED BY public.notification_global_settings.id;


--
-- Name: notification_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_history (
    id bigint NOT NULL,
    evento_codigo character varying(80) NOT NULL,
    canal character varying(30) NOT NULL,
    destinatario character varying(200) NOT NULL,
    asunto character varying(300),
    mensaje text,
    estado character varying(20) DEFAULT 'pendiente'::character varying NOT NULL,
    enviado_en timestamp without time zone,
    error_detalle text,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: notification_history_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notification_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notification_history_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.notification_history_id_seq OWNED BY public.notification_history.id;


--
-- Name: notification_recipients; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_recipients (
    id bigint NOT NULL,
    evento_id bigint NOT NULL,
    rol_id bigint NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: notification_recipients_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notification_recipients_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notification_recipients_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.notification_recipients_id_seq OWNED BY public.notification_recipients.id;


--
-- Name: notification_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_settings (
    id bigint NOT NULL,
    evento_id bigint NOT NULL,
    canal character varying(30) NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    prioridad character varying(20) DEFAULT 'normal'::character varying NOT NULL,
    frecuencia character varying(30) DEFAULT 'inmediata'::character varying NOT NULL,
    horario_inicio time without time zone,
    horario_fin time without time zone,
    recordatorio_activo boolean DEFAULT false NOT NULL,
    recordatorio_horas integer DEFAULT 0,
    vencimiento_horas integer DEFAULT 0,
    mensaje_personalizado text,
    plantilla_id bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    creado_por character varying(150),
    modificado_por character varying(150),
    ip_creacion character varying(50),
    ip_modificacion character varying(50)
);


--
-- Name: notification_settings_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notification_settings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notification_settings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.notification_settings_id_seq OWNED BY public.notification_settings.id;


--
-- Name: notification_templates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_templates (
    id bigint NOT NULL,
    nombre character varying(100) NOT NULL,
    tipo character varying(20) DEFAULT 'email'::character varying NOT NULL,
    asunto character varying(200),
    cuerpo text,
    activo boolean DEFAULT true NOT NULL,
    evento_id bigint,
    idioma character varying(10) DEFAULT 'es'::character varying,
    variables text DEFAULT '[]'::text,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


--
-- Name: notification_templates_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.notification_templates_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notification_templates_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.notification_templates_id_seq OWNED BY public.notification_templates.id;


--
-- Name: org_units; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.org_units (
    id bigint NOT NULL,
    nombre character varying(200) NOT NULL,
    siglas character varying(20),
    nivel integer DEFAULT 0 NOT NULL,
    padre_id bigint,
    responsable character varying(200),
    activo boolean DEFAULT true NOT NULL,
    codigo character varying(50),
    descripcion character varying(500),
    tipo character varying(50) DEFAULT 'Unidad'::character varying,
    telefono character varying(30),
    correo character varying(200),
    orden integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


--
-- Name: org_units_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.org_units_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: org_units_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.org_units_id_seq OWNED BY public.org_units.id;


--
-- Name: organization_departments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.organization_departments (
    id bigint NOT NULL,
    organization_id bigint NOT NULL,
    nombre character varying(200) NOT NULL,
    director character varying(200) DEFAULT ''::character varying NOT NULL,
    director_cargo character varying(200) DEFAULT ''::character varying NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    CONSTRAINT organization_departments_nombre_check CHECK ((length(TRIM(BOTH FROM nombre)) > 0))
);


--
-- Name: organization_departments_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.organization_departments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: organization_departments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.organization_departments_id_seq OWNED BY public.organization_departments.id;


--
-- Name: organization_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.organization_settings (
    id bigint NOT NULL,
    organization_id bigint NOT NULL,
    clave character varying(100) NOT NULL,
    valor text DEFAULT ''::text,
    descripcion character varying(300) DEFAULT ''::character varying,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now()
);


--
-- Name: organization_settings_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.organization_settings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: organization_settings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.organization_settings_id_seq OWNED BY public.organization_settings.id;


--
-- Name: organizations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.organizations (
    id bigint NOT NULL,
    nombre character varying(300) NOT NULL,
    siglas character varying(30),
    codigo_institucional character varying(50),
    tipo_organizacion character varying(50) DEFAULT 'Institucion'::character varying,
    ruc character varying(30),
    director character varying(200),
    estado character varying(30) DEFAULT 'ACTIVO'::character varying,
    activo boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    director_cargo character varying(200)
);


--
-- Name: organizations_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.organizations_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: organizations_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.organizations_id_seq OWNED BY public.organizations.id;


--
-- Name: parameter_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.parameter_history (
    id bigint NOT NULL,
    parametro_id bigint NOT NULL,
    clave character varying(100) NOT NULL,
    valor_anterior text,
    valor_nuevo text,
    usuario character varying(150) NOT NULL,
    accion character varying(50) NOT NULL,
    direccion_ip character varying(45),
    fecha timestamp without time zone DEFAULT now() NOT NULL,
    created_at timestamp without time zone DEFAULT now()
);


--
-- Name: parameter_history_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.parameter_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: parameter_history_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.parameter_history_id_seq OWNED BY public.parameter_history.id;


--
-- Name: parametros; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.parametros (
    id bigint NOT NULL,
    grupo character varying(100) NOT NULL,
    clave character varying(100) NOT NULL,
    valor text DEFAULT ''::text NOT NULL,
    tipo character varying(50) DEFAULT 'texto'::character varying NOT NULL,
    descripcion character varying(255),
    codigo character varying(100),
    nombre character varying(200),
    valor_defecto text,
    categoria character varying(50) DEFAULT 'General'::character varying,
    estado character varying(30) DEFAULT 'ACTIVO'::character varying,
    visible boolean DEFAULT true,
    editable boolean DEFAULT true,
    requiere_reinicio boolean DEFAULT false,
    modulo_id bigint,
    orden integer DEFAULT 0,
    observaciones text,
    fecha_vigencia timestamp without time zone,
    fecha_expiracion timestamp without time zone,
    created_at timestamp without time zone DEFAULT now(),
    updated_at timestamp without time zone DEFAULT now(),
    activo boolean DEFAULT true NOT NULL
);


--
-- Name: parametros_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.parametros_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: parametros_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.parametros_id_seq OWNED BY public.parametros.id;


--
-- Name: password_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.password_history (
    id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    password_hash character varying(255) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: password_history_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.password_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: password_history_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.password_history_id_seq OWNED BY public.password_history.id;


--
-- Name: password_reset_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.password_reset_requests (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    code_hash character varying(255) NOT NULL,
    reset_token_hash character varying(255),
    expires_at timestamp without time zone NOT NULL,
    reset_token_expires_at timestamp without time zone,
    attempts integer DEFAULT 0 NOT NULL,
    used boolean DEFAULT false NOT NULL,
    blocked boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    code_verified_at timestamp without time zone,
    used_at timestamp without time zone,
    request_ip character varying(45)
);


--
-- Name: password_reset_requests_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.password_reset_requests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: password_reset_requests_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.password_reset_requests_id_seq OWNED BY public.password_reset_requests.id;


--
-- Name: permisos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permisos (
    id bigint NOT NULL,
    modulo character varying(100) NOT NULL,
    accion character varying(100) NOT NULL,
    recurso character varying(100) NOT NULL,
    descripcion character varying(255),
    activo boolean DEFAULT true NOT NULL
);


--
-- Name: permisos_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.permisos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: permisos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.permisos_id_seq OWNED BY public.permisos.id;


--
-- Name: reporte_programaciones; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reporte_programaciones (
    id bigint NOT NULL,
    nombre character varying(300) NOT NULL,
    modulo character varying(100) NOT NULL,
    tipo character varying(50) DEFAULT 'ejecutivo'::character varying NOT NULL,
    formato character varying(50) DEFAULT 'PDF'::character varying NOT NULL,
    periodicidad character varying(20) NOT NULL,
    fecha_inicio date NOT NULL,
    hora time without time zone NOT NULL,
    destinatarios character varying(2000) DEFAULT ''::character varying NOT NULL,
    creado_por character varying(200) DEFAULT 'sistema'::character varying NOT NULL,
    archivos_ruta character varying(2000) DEFAULT ''::character varying NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    proxima_ejecucion timestamp without time zone NOT NULL,
    ultima_ejecucion timestamp without time zone,
    ultimo_estado character varying(20) DEFAULT 'PENDIENTE'::character varying NOT NULL,
    ultimo_error character varying(1000) DEFAULT ''::character varying NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_reporte_programacion_periodicidad CHECK (((periodicidad)::text = ANY (ARRAY[('diario'::character varying)::text, ('semanal'::character varying)::text, ('mensual'::character varying)::text])))
);


--
-- Name: reporte_programaciones_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.reporte_programaciones_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reporte_programaciones_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.reporte_programaciones_id_seq OWNED BY public.reporte_programaciones.id;


--
-- Name: reportes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reportes (
    id bigint NOT NULL,
    nombre character varying(300) NOT NULL,
    modulo character varying(100) NOT NULL,
    tipo character varying(50) DEFAULT 'ejecutivo'::character varying NOT NULL,
    fecha date DEFAULT CURRENT_DATE,
    generado_por character varying(200) DEFAULT ''::character varying,
    formato character varying(50) DEFAULT 'PDF'::character varying,
    descripcion text DEFAULT ''::text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    archivos_ruta character varying(2000) DEFAULT ''::character varying,
    contenido text DEFAULT ''::text NOT NULL,
    creado_por_usuario_id bigint,
    CONSTRAINT reportes_formato_check CHECK (((formato)::text = ANY (ARRAY[('PDF'::character varying)::text, ('Excel'::character varying)::text, ('Word'::character varying)::text, ('CSV'::character varying)::text]))),
    CONSTRAINT reportes_modulo_check CHECK (((modulo)::text = ANY (ARRAY[('Reuniones'::character varying)::text, ('Actas'::character varying)::text, ('Asistencia'::character varying)::text, ('Actividades'::character varying)::text, ('Inventario'::character varying)::text, ('Tareas'::character varying)::text, ('Documentos'::character varying)::text, ('Usuarios'::character varying)::text]))),
    CONSTRAINT reportes_tipo_check CHECK (((tipo)::text = ANY (ARRAY[('analitico'::character varying)::text, ('ejecutivo'::character varying)::text, ('detallado'::character varying)::text])))
);


--
-- Name: reportes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.reportes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reportes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.reportes_id_seq OWNED BY public.reportes.id;


--
-- Name: reunion_participantes_usuario; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reunion_participantes_usuario (
    reunion_id bigint NOT NULL,
    usuario_id bigint NOT NULL
);


--
-- Name: reunion_unidades; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reunion_unidades (
    reunion_id bigint NOT NULL,
    unidad_id bigint NOT NULL
);


--
-- Name: reuniones; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.reuniones (
    id bigint NOT NULL,
    fecha date DEFAULT CURRENT_DATE NOT NULL,
    fecha_iso character varying(20) DEFAULT ''::character varying,
    hora_inicio time without time zone DEFAULT '08:00:00'::time without time zone NOT NULL,
    hora_fin time without time zone DEFAULT '09:00:00'::time without time zone NOT NULL,
    titulo character varying(300) NOT NULL,
    subtitulo character varying(500) DEFAULT ''::character varying,
    area character varying(200) DEFAULT ''::character varying,
    organizador character varying(200) DEFAULT ''::character varying,
    lugar character varying(200) DEFAULT ''::character varying,
    modalidad character varying(50) DEFAULT 'Presencial'::character varying,
    estado character varying(50) DEFAULT 'programada'::character varying,
    participantes integer DEFAULT 0,
    descripcion text DEFAULT ''::text,
    participantes_nombres text DEFAULT '[]'::text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    codigo character varying(30) NOT NULL,
    objetivo text DEFAULT ''::text,
    tipo_reunion character varying(50) DEFAULT 'General'::character varying,
    enlace_virtual character varying(500) DEFAULT ''::character varying,
    sala character varying(200) DEFAULT ''::character varying,
    unidad_responsable character varying(200) DEFAULT ''::character varying,
    organizacion character varying(200) DEFAULT ''::character varying,
    observaciones text DEFAULT ''::text,
    lock_version bigint DEFAULT 0 NOT NULL,
    organizador_usuario_id bigint,
    CONSTRAINT reuniones_estado_check CHECK (((estado)::text = ANY (ARRAY[('programada'::character varying)::text, ('en_curso'::character varying)::text, ('realizada'::character varying)::text, ('cancelada'::character varying)::text]))),
    CONSTRAINT reuniones_modalidad_check CHECK (((modalidad)::text = ANY (ARRAY[('Presencial'::character varying)::text, ('Virtual'::character varying)::text, ('Mixta'::character varying)::text])))
);


--
-- Name: reuniones_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.reuniones_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: reuniones_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.reuniones_id_seq OWNED BY public.reuniones.id;


--
-- Name: rol_permisos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rol_permisos (
    id bigint NOT NULL,
    rol_id bigint NOT NULL,
    permiso_id bigint NOT NULL
);


--
-- Name: rol_permisos_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.rol_permisos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: rol_permisos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.rol_permisos_id_seq OWNED BY public.rol_permisos.id;


--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roles (
    id bigint NOT NULL,
    nombre character varying(100) NOT NULL,
    descripcion character varying(255),
    activo boolean DEFAULT true NOT NULL,
    sistema boolean DEFAULT false NOT NULL,
    lock_version bigint DEFAULT 0 NOT NULL
);


--
-- Name: roles_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.roles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: roles_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.roles_id_seq OWNED BY public.roles.id;


--
-- Name: security_policies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.security_policies (
    id bigint NOT NULL,
    nombre character varying(255) NOT NULL,
    valor character varying(255) NOT NULL,
    descripcion character varying(255),
    categoria character varying(100) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    lock_version bigint DEFAULT 0 NOT NULL
);


--
-- Name: security_policies_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.security_policies_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: security_policies_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.security_policies_id_seq OWNED BY public.security_policies.id;


--
-- Name: solicitud_aprobaciones; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.solicitud_aprobaciones (
    id bigint NOT NULL,
    solicitud_id bigint NOT NULL,
    etapa_id bigint NOT NULL,
    aprobador_id bigint NOT NULL,
    estado character varying(20) DEFAULT 'PENDIENTE'::character varying NOT NULL,
    comentario text DEFAULT ''::text NOT NULL,
    asignada_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    fecha_limite timestamp without time zone NOT NULL,
    resuelta_at timestamp without time zone,
    escalada_at timestamp without time zone,
    escalada_a_id bigint,
    CONSTRAINT solicitud_aprobaciones_estado_check CHECK (((estado)::text = ANY (ARRAY[('PENDIENTE'::character varying)::text, ('APROBADA'::character varying)::text, ('RECHAZADA'::character varying)::text, ('CANCELADA'::character varying)::text])))
);


--
-- Name: solicitud_aprobaciones_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.solicitud_aprobaciones_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: solicitud_aprobaciones_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.solicitud_aprobaciones_id_seq OWNED BY public.solicitud_aprobaciones.id;


--
-- Name: solicitud_etapas; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.solicitud_etapas (
    id bigint NOT NULL,
    tipo_id bigint NOT NULL,
    nombre character varying(150) NOT NULL,
    orden integer NOT NULL,
    aprobador_rol character varying(100),
    aprobador_usuario_id bigint,
    sla_horas integer DEFAULT 24 NOT NULL,
    escalamiento_rol character varying(100) DEFAULT 'administrador'::character varying,
    activo boolean DEFAULT true NOT NULL,
    CONSTRAINT solicitud_etapas_check CHECK (((aprobador_rol IS NOT NULL) OR (aprobador_usuario_id IS NOT NULL))),
    CONSTRAINT solicitud_etapas_orden_check CHECK ((orden > 0)),
    CONSTRAINT solicitud_etapas_sla_horas_check CHECK ((sla_horas > 0))
);


--
-- Name: solicitud_etapas_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.solicitud_etapas_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: solicitud_etapas_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.solicitud_etapas_id_seq OWNED BY public.solicitud_etapas.id;


--
-- Name: solicitud_historial; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.solicitud_historial (
    id bigint NOT NULL,
    solicitud_id bigint NOT NULL,
    usuario_id bigint,
    accion character varying(80) NOT NULL,
    estado_anterior character varying(30),
    estado_nuevo character varying(30),
    detalle text DEFAULT ''::text NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: solicitud_historial_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.solicitud_historial_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: solicitud_historial_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.solicitud_historial_id_seq OWNED BY public.solicitud_historial.id;


--
-- Name: solicitud_tipos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.solicitud_tipos (
    id bigint NOT NULL,
    codigo character varying(60) NOT NULL,
    nombre character varying(150) NOT NULL,
    descripcion character varying(500) DEFAULT ''::character varying NOT NULL,
    formulario_schema jsonb DEFAULT '[]'::jsonb NOT NULL,
    sla_horas integer DEFAULT 72 NOT NULL,
    requiere_adjunto boolean DEFAULT false NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT solicitud_tipos_sla_horas_check CHECK ((sla_horas > 0))
);


--
-- Name: solicitud_tipos_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.solicitud_tipos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: solicitud_tipos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.solicitud_tipos_id_seq OWNED BY public.solicitud_tipos.id;


--
-- Name: solicitudes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.solicitudes (
    id bigint NOT NULL,
    codigo character varying(40),
    tipo_id bigint NOT NULL,
    solicitante_id bigint NOT NULL,
    etapa_actual_id bigint,
    asunto character varying(200) NOT NULL,
    descripcion text DEFAULT ''::text NOT NULL,
    datos_formulario jsonb DEFAULT '{}'::jsonb NOT NULL,
    estado character varying(30) DEFAULT 'BORRADOR'::character varying NOT NULL,
    prioridad character varying(20) DEFAULT 'MEDIA'::character varying NOT NULL,
    fecha_limite timestamp without time zone,
    enviada_at timestamp without time zone,
    resuelta_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT solicitudes_estado_check CHECK (((estado)::text = ANY (ARRAY[('BORRADOR'::character varying)::text, ('EN_APROBACION'::character varying)::text, ('APROBADA'::character varying)::text, ('RECHAZADA'::character varying)::text, ('CANCELADA'::character varying)::text]))),
    CONSTRAINT solicitudes_prioridad_check CHECK (((prioridad)::text = ANY (ARRAY[('BAJA'::character varying)::text, ('MEDIA'::character varying)::text, ('ALTA'::character varying)::text, ('CRITICA'::character varying)::text])))
);


--
-- Name: solicitudes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.solicitudes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: solicitudes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.solicitudes_id_seq OWNED BY public.solicitudes.id;


--
-- Name: tarea_adjuntos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tarea_adjuntos (
    id bigint NOT NULL,
    tarea_id bigint NOT NULL,
    nombre character varying(300) NOT NULL,
    tamano character varying(50) DEFAULT ''::character varying,
    ruta_archivo character varying(1000),
    tipo_contenido character varying(100),
    tamano_bytes bigint
);


--
-- Name: tarea_adjuntos_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tarea_adjuntos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tarea_adjuntos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tarea_adjuntos_id_seq OWNED BY public.tarea_adjuntos.id;


--
-- Name: tarea_checklist; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tarea_checklist (
    id bigint NOT NULL,
    tarea_id bigint NOT NULL,
    texto character varying(500) NOT NULL,
    completado boolean DEFAULT false
);


--
-- Name: tarea_checklist_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tarea_checklist_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tarea_checklist_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tarea_checklist_id_seq OWNED BY public.tarea_checklist.id;


--
-- Name: tarea_participantes_usuario; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tarea_participantes_usuario (
    tarea_id bigint NOT NULL,
    usuario_id bigint NOT NULL
);


--
-- Name: tareas; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tareas (
    id bigint NOT NULL,
    titulo character varying(300) NOT NULL,
    descripcion text DEFAULT ''::text,
    proyecto character varying(200) DEFAULT ''::character varying,
    codigo character varying(50) NOT NULL,
    tipo character varying(100) DEFAULT 'Configuración'::character varying,
    asignado character varying(200) DEFAULT ''::character varying,
    cargo character varying(200) DEFAULT ''::character varying,
    prioridad character varying(50) DEFAULT 'media'::character varying,
    fecha_limite date,
    fecha_creacion date DEFAULT CURRENT_DATE,
    estado character varying(50) DEFAULT 'por_hacer'::character varying,
    creado_por character varying(200) DEFAULT ''::character varying,
    progreso integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    unidades character varying(500) DEFAULT ''::character varying NOT NULL,
    hora_limite time without time zone,
    lock_version bigint DEFAULT 0 NOT NULL,
    asignado_usuario_id bigint,
    acta_id bigint,
    acuerdo_clave character varying(64),
    CONSTRAINT tareas_estado_check CHECK (((estado)::text = ANY (ARRAY[('por_hacer'::character varying)::text, ('en_progreso'::character varying)::text, ('completada'::character varying)::text, ('vencida'::character varying)::text]))),
    CONSTRAINT tareas_prioridad_check CHECK (((prioridad)::text = ANY (ARRAY[('alta'::character varying)::text, ('media'::character varying)::text, ('baja'::character varying)::text])))
);


--
-- Name: tareas_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tareas_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tareas_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tareas_id_seq OWNED BY public.tareas.id;


--
-- Name: tipos_documento; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tipos_documento (
    id bigint NOT NULL,
    codigo character varying(10) NOT NULL,
    nombre character varying(100) NOT NULL,
    descripcion character varying(255),
    activo boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now()
);


--
-- Name: tipos_documento_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tipos_documento_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tipos_documento_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tipos_documento_id_seq OWNED BY public.tipos_documento.id;


--
-- Name: ubicaciones; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ubicaciones (
    id bigint NOT NULL,
    nombre character varying(200) NOT NULL,
    direccion character varying(300),
    latitud double precision DEFAULT 0 NOT NULL,
    longitud double precision DEFAULT 0 NOT NULL,
    radio double precision DEFAULT 200 NOT NULL
);


--
-- Name: ubicaciones_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.ubicaciones_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ubicaciones_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.ubicaciones_id_seq OWNED BY public.ubicaciones.id;


--
-- Name: unidades; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.unidades (
    id bigint NOT NULL,
    nombre character varying(150) NOT NULL,
    descripcion character varying(255),
    activo boolean DEFAULT true NOT NULL
);


--
-- Name: unidades_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.unidades_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: unidades_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.unidades_id_seq OWNED BY public.unidades.id;


--
-- Name: user_preferences; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_preferences (
    id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    theme character varying(20) DEFAULT 'light'::character varying NOT NULL,
    language character varying(10) DEFAULT 'es'::character varying NOT NULL,
    timezone character varying(100) DEFAULT 'America/Panama'::character varying NOT NULL,
    date_format character varying(20) DEFAULT 'DD/MM/YYYY'::character varying NOT NULL,
    time_format character varying(20) DEFAULT 'HH:mm'::character varying NOT NULL,
    home_page character varying(100) DEFAULT 'dashboard'::character varying NOT NULL,
    records_per_page integer DEFAULT 20 NOT NULL,
    sidebar_expanded boolean DEFAULT true NOT NULL,
    animations_enabled boolean DEFAULT true NOT NULL,
    email_notifications boolean DEFAULT true NOT NULL,
    push_notifications boolean DEFAULT true NOT NULL,
    sound_enabled boolean DEFAULT true NOT NULL,
    confirmations_enabled boolean DEFAULT true NOT NULL,
    custom_dashboard text,
    quick_access_cards text,
    font_size character varying(20) DEFAULT 'medium'::character varying NOT NULL,
    high_contrast boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: user_preferences_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.user_preferences_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_preferences_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.user_preferences_id_seq OWNED BY public.user_preferences.id;


--
-- Name: usuario_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.usuario_roles (
    id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    rol_id bigint NOT NULL
);


--
-- Name: usuario_roles_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.usuario_roles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: usuario_roles_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.usuario_roles_id_seq OWNED BY public.usuario_roles.id;


--
-- Name: usuario_unidad; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.usuario_unidad (
    id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    unidad_id bigint NOT NULL,
    rol_id bigint NOT NULL
);


--
-- Name: usuario_unidad_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.usuario_unidad_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: usuario_unidad_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.usuario_unidad_id_seq OWNED BY public.usuario_unidad.id;


--
-- Name: usuarios; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.usuarios (
    id bigint NOT NULL,
    nombre character varying(150) NOT NULL,
    apellido character varying(150) DEFAULT ''::character varying NOT NULL,
    correo character varying(150) NOT NULL,
    password character varying(255) NOT NULL,
    activo boolean DEFAULT true NOT NULL,
    bloqueado boolean DEFAULT false NOT NULL,
    creado_en timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    ultimo_acceso timestamp without time zone,
    intentos_fallidos integer DEFAULT 0,
    bloqueado_hasta timestamp without time zone,
    segundo_nombre character varying(150),
    documento_identidad character varying(50),
    tipo_documento character varying(20) DEFAULT 'CEDULA'::character varying,
    correo_alterno character varying(150),
    telefono character varying(30),
    celular character varying(30),
    cargo character varying(150),
    departamento character varying(150),
    direccion character varying(255),
    unidad_administrativa character varying(150),
    username character varying(100) NOT NULL,
    fecha_expiracion timestamp without time zone,
    cambio_contrasena_requerido boolean DEFAULT false NOT NULL,
    fotografia_ruta character varying(500),
    codigo_interno character varying(50),
    observaciones text,
    actualizado_en timestamp without time zone,
    institucion character varying(200),
    token_version bigint DEFAULT 0 NOT NULL,
    contrasena_temporal_expira_en timestamp without time zone,
    CONSTRAINT usuarios_username_not_blank CHECK ((btrim((username)::text) <> ''::text))
);


--
-- Name: usuarios_2fa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.usuarios_2fa (
    id bigint NOT NULL,
    usuario_id bigint NOT NULL,
    enabled boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: usuarios_2fa_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.usuarios_2fa_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: usuarios_2fa_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.usuarios_2fa_id_seq OWNED BY public.usuarios_2fa.id;


--
-- Name: usuarios_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.usuarios_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: usuarios_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.usuarios_id_seq OWNED BY public.usuarios.id;


--
-- Name: vw_usuario_roles; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.vw_usuario_roles AS
 SELECT ur.usuario_id,
    r.nombre AS rol
   FROM (public.usuario_roles ur
     JOIN public.roles r ON ((r.id = ur.rol_id)))
  WHERE (r.activo = true)
UNION
 SELECT uu.usuario_id,
    r.nombre AS rol
   FROM (public.usuario_unidad uu
     JOIN public.roles r ON ((r.id = uu.rol_id)))
  WHERE (r.activo = true);


--
-- Name: acta_acuerdos id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acta_acuerdos ALTER COLUMN id SET DEFAULT nextval('public.acta_acuerdos_id_seq'::regclass);


--
-- Name: acta_adjuntos id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acta_adjuntos ALTER COLUMN id SET DEFAULT nextval('public.acta_adjuntos_id_seq'::regclass);


--
-- Name: acta_firmas id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acta_firmas ALTER COLUMN id SET DEFAULT nextval('public.acta_firmas_id_seq'::regclass);


--
-- Name: acta_participantes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acta_participantes ALTER COLUMN id SET DEFAULT nextval('public.acta_participantes_id_seq'::regclass);


--
-- Name: actas id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.actas ALTER COLUMN id SET DEFAULT nextval('public.actas_id_seq'::regclass);


--
-- Name: admin_password_reset_requests id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_password_reset_requests ALTER COLUMN id SET DEFAULT nextval('public.admin_password_reset_requests_id_seq'::regclass);


--
-- Name: allowed_http_headers id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.allowed_http_headers ALTER COLUMN id SET DEFAULT nextval('public.allowed_http_headers_id_seq'::regclass);


--
-- Name: allowed_http_methods id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.allowed_http_methods ALTER COLUMN id SET DEFAULT nextval('public.allowed_http_methods_id_seq'::regclass);


--
-- Name: allowed_origins id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.allowed_origins ALTER COLUMN id SET DEFAULT nextval('public.allowed_origins_id_seq'::regclass);


--
-- Name: asistencia_invitados id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asistencia_invitados ALTER COLUMN id SET DEFAULT nextval('public.asistencia_invitados_id_seq'::regclass);


--
-- Name: asistencia_registros id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asistencia_registros ALTER COLUMN id SET DEFAULT nextval('public.asistencia_registros_id_seq'::regclass);


--
-- Name: asistencia_sesiones id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asistencia_sesiones ALTER COLUMN id SET DEFAULT nextval('public.asistencia_sesiones_id_seq'::regclass);


--
-- Name: audit_logs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs ALTER COLUMN id SET DEFAULT nextval('public.audit_logs_id_seq'::regclass);


--
-- Name: backups id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.backups ALTER COLUMN id SET DEFAULT nextval('public.backups_id_seq'::regclass);


--
-- Name: carpeta_documentos id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carpeta_documentos ALTER COLUMN id SET DEFAULT nextval('public.carpeta_documentos_id_seq'::regclass);


--
-- Name: carpeta_usuarios id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carpeta_usuarios ALTER COLUMN id SET DEFAULT nextval('public.carpeta_usuarios_id_seq'::regclass);


--
-- Name: carpetas id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carpetas ALTER COLUMN id SET DEFAULT nextval('public.carpetas_id_seq'::regclass);


--
-- Name: documento_solicitudes_aprobacion id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_solicitudes_aprobacion ALTER COLUMN id SET DEFAULT nextval('public.documento_solicitudes_aprobacion_id_seq'::regclass);


--
-- Name: documento_usuarios id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_usuarios ALTER COLUMN id SET DEFAULT nextval('public.documento_usuarios_id_seq'::regclass);


--
-- Name: documento_versiones id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_versiones ALTER COLUMN id SET DEFAULT nextval('public.documento_versiones_id_seq'::regclass);


--
-- Name: documentos id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documentos ALTER COLUMN id SET DEFAULT nextval('public.documentos_id_seq'::regclass);


--
-- Name: grupo_mensajes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grupo_mensajes ALTER COLUMN id SET DEFAULT nextval('public.grupo_mensajes_id_seq'::regclass);


--
-- Name: grupo_permisos id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grupo_permisos ALTER COLUMN id SET DEFAULT nextval('public.grupo_permisos_id_seq'::regclass);


--
-- Name: grupo_usuarios id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grupo_usuarios ALTER COLUMN id SET DEFAULT nextval('public.grupo_usuarios_id_seq'::regclass);


--
-- Name: grupos id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grupos ALTER COLUMN id SET DEFAULT nextval('public.grupos_id_seq'::regclass);


--
-- Name: horarios id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.horarios ALTER COLUMN id SET DEFAULT nextval('public.horarios_id_seq'::regclass);


--
-- Name: inventario_activos id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventario_activos ALTER COLUMN id SET DEFAULT nextval('public.inventario_activos_id_seq'::regclass);


--
-- Name: ip_restrictions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ip_restrictions ALTER COLUMN id SET DEFAULT nextval('public.ip_restrictions_id_seq'::regclass);


--
-- Name: memo_historial id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.memo_historial ALTER COLUMN id SET DEFAULT nextval('public.memo_historial_id_seq'::regclass);


--
-- Name: memo_numeros id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.memo_numeros ALTER COLUMN id SET DEFAULT nextval('public.memo_numeros_id_seq'::regclass);


--
-- Name: memos id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.memos ALTER COLUMN id SET DEFAULT nextval('public.memos_id_seq'::regclass);


--
-- Name: modulos id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.modulos ALTER COLUMN id SET DEFAULT nextval('public.modulos_id_seq'::regclass);


--
-- Name: nota_historial id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.nota_historial ALTER COLUMN id SET DEFAULT nextval('public.nota_historial_id_seq'::regclass);


--
-- Name: nota_numeros id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.nota_numeros ALTER COLUMN id SET DEFAULT nextval('public.nota_numeros_id_seq'::regclass);


--
-- Name: nota_pdfs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.nota_pdfs ALTER COLUMN id SET DEFAULT nextval('public.nota_pdfs_id_seq'::regclass);


--
-- Name: nota_relaciones id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.nota_relaciones ALTER COLUMN id SET DEFAULT nextval('public.nota_relaciones_id_seq'::regclass);


--
-- Name: notas id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notas ALTER COLUMN id SET DEFAULT nextval('public.notas_id_seq'::regclass);


--
-- Name: notificaciones id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notificaciones ALTER COLUMN id SET DEFAULT nextval('public.notificaciones_id_seq'::regclass);


--
-- Name: notification_channels id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_channels ALTER COLUMN id SET DEFAULT nextval('public.notification_channels_id_seq'::regclass);


--
-- Name: notification_events id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_events ALTER COLUMN id SET DEFAULT nextval('public.notification_events_id_seq'::regclass);


--
-- Name: notification_global_settings id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_global_settings ALTER COLUMN id SET DEFAULT nextval('public.notification_global_settings_id_seq'::regclass);


--
-- Name: notification_history id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_history ALTER COLUMN id SET DEFAULT nextval('public.notification_history_id_seq'::regclass);


--
-- Name: notification_recipients id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_recipients ALTER COLUMN id SET DEFAULT nextval('public.notification_recipients_id_seq'::regclass);


--
-- Name: notification_settings id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_settings ALTER COLUMN id SET DEFAULT nextval('public.notification_settings_id_seq'::regclass);


--
-- Name: notification_templates id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_templates ALTER COLUMN id SET DEFAULT nextval('public.notification_templates_id_seq'::regclass);


--
-- Name: org_units id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.org_units ALTER COLUMN id SET DEFAULT nextval('public.org_units_id_seq'::regclass);


--
-- Name: organization_departments id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization_departments ALTER COLUMN id SET DEFAULT nextval('public.organization_departments_id_seq'::regclass);


--
-- Name: organization_settings id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization_settings ALTER COLUMN id SET DEFAULT nextval('public.organization_settings_id_seq'::regclass);


--
-- Name: organizations id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organizations ALTER COLUMN id SET DEFAULT nextval('public.organizations_id_seq'::regclass);


--
-- Name: parameter_history id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parameter_history ALTER COLUMN id SET DEFAULT nextval('public.parameter_history_id_seq'::regclass);


--
-- Name: parametros id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parametros ALTER COLUMN id SET DEFAULT nextval('public.parametros_id_seq'::regclass);


--
-- Name: password_history id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_history ALTER COLUMN id SET DEFAULT nextval('public.password_history_id_seq'::regclass);


--
-- Name: password_reset_requests id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_requests ALTER COLUMN id SET DEFAULT nextval('public.password_reset_requests_id_seq'::regclass);


--
-- Name: permisos id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permisos ALTER COLUMN id SET DEFAULT nextval('public.permisos_id_seq'::regclass);


--
-- Name: reporte_programaciones id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reporte_programaciones ALTER COLUMN id SET DEFAULT nextval('public.reporte_programaciones_id_seq'::regclass);


--
-- Name: reportes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reportes ALTER COLUMN id SET DEFAULT nextval('public.reportes_id_seq'::regclass);


--
-- Name: reuniones id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reuniones ALTER COLUMN id SET DEFAULT nextval('public.reuniones_id_seq'::regclass);


--
-- Name: rol_permisos id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rol_permisos ALTER COLUMN id SET DEFAULT nextval('public.rol_permisos_id_seq'::regclass);


--
-- Name: roles id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles ALTER COLUMN id SET DEFAULT nextval('public.roles_id_seq'::regclass);


--
-- Name: security_policies id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.security_policies ALTER COLUMN id SET DEFAULT nextval('public.security_policies_id_seq'::regclass);


--
-- Name: solicitud_aprobaciones id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_aprobaciones ALTER COLUMN id SET DEFAULT nextval('public.solicitud_aprobaciones_id_seq'::regclass);


--
-- Name: solicitud_etapas id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_etapas ALTER COLUMN id SET DEFAULT nextval('public.solicitud_etapas_id_seq'::regclass);


--
-- Name: solicitud_historial id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_historial ALTER COLUMN id SET DEFAULT nextval('public.solicitud_historial_id_seq'::regclass);


--
-- Name: solicitud_tipos id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_tipos ALTER COLUMN id SET DEFAULT nextval('public.solicitud_tipos_id_seq'::regclass);


--
-- Name: solicitudes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitudes ALTER COLUMN id SET DEFAULT nextval('public.solicitudes_id_seq'::regclass);


--
-- Name: tarea_adjuntos id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tarea_adjuntos ALTER COLUMN id SET DEFAULT nextval('public.tarea_adjuntos_id_seq'::regclass);


--
-- Name: tarea_checklist id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tarea_checklist ALTER COLUMN id SET DEFAULT nextval('public.tarea_checklist_id_seq'::regclass);


--
-- Name: tareas id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tareas ALTER COLUMN id SET DEFAULT nextval('public.tareas_id_seq'::regclass);


--
-- Name: tipos_documento id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tipos_documento ALTER COLUMN id SET DEFAULT nextval('public.tipos_documento_id_seq'::regclass);


--
-- Name: ubicaciones id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ubicaciones ALTER COLUMN id SET DEFAULT nextval('public.ubicaciones_id_seq'::regclass);


--
-- Name: unidades id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.unidades ALTER COLUMN id SET DEFAULT nextval('public.unidades_id_seq'::regclass);


--
-- Name: user_preferences id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_preferences ALTER COLUMN id SET DEFAULT nextval('public.user_preferences_id_seq'::regclass);


--
-- Name: usuario_roles id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_roles ALTER COLUMN id SET DEFAULT nextval('public.usuario_roles_id_seq'::regclass);


--
-- Name: usuario_unidad id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_unidad ALTER COLUMN id SET DEFAULT nextval('public.usuario_unidad_id_seq'::regclass);


--
-- Name: usuarios id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuarios ALTER COLUMN id SET DEFAULT nextval('public.usuarios_id_seq'::regclass);


--
-- Name: usuarios_2fa id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuarios_2fa ALTER COLUMN id SET DEFAULT nextval('public.usuarios_2fa_id_seq'::regclass);


--
-- Data for Name: acta_acuerdos; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: acta_adjuntos; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: acta_firmas; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: acta_participantes; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: actas; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: admin_password_reset_requests; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: allowed_http_headers; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: allowed_http_methods; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: allowed_origins; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: asistencia_invitados; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: asistencia_registros; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: asistencia_sesiones; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: audit_logs; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: backups; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: carpeta_documentos; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: carpeta_usuarios; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: carpetas; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: documento_solicitudes_aprobacion; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: documento_usuarios; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: documento_versiones; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: documentos; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: grupo_mensajes; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: grupo_permisos; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: grupo_usuarios; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: grupos; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: horarios; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: inventario_activos; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: ip_restrictions; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: memo_historial; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: memo_numeros; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: memos; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: modulos; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.modulos (id, nombre, ruta, icono, activo, orden, codigo, descripcion, categoria, color, padre_id, ruta_api, tipo_modulo, nivel_jerarquico, breadcrumb, pagina_inicio, url_ayuda, etiqueta, componente_angular, visible_en_menu, visible_en_dashboard, permite_acceso_directo, requiere_autenticacion, created_at, updated_at) VALUES
	(1, 'Panel', '/dashboard', 'grid-outline', true, 1, 'panel', 'Panel principal de navegación del sistema', 'principal', '#6366f1', NULL, NULL, 'MODULO', 0, NULL, NULL, NULL, NULL, NULL, true, true, false, true, '2026-10-12 08:59:08.662218', '2026-10-12 08:59:08.662218'),
	(2, 'Reuniones', '/reuniones', 'calendar-outline', true, 2, 'reuniones', 'Gestión de reuniones y sesiones de trabajo', 'academico', '#0ea5e9', NULL, NULL, 'MODULO', 0, NULL, NULL, NULL, NULL, NULL, true, true, false, true, '2026-10-12 08:59:08.662218', '2026-10-12 08:59:08.662218'),
	(3, 'Actas', '/actas', 'document-text-outline', true, 3, 'actas', 'Gestión de actas de reuniones con firmas y seguimiento', 'academico', '#8b5cf6', NULL, NULL, 'MODULO', 0, NULL, NULL, NULL, NULL, NULL, true, true, false, true, '2026-10-12 08:59:08.662218', '2026-10-12 08:59:08.662218'),
	(4, 'Configuración QR', '/asistencia-qr', 'qr-code-outline', true, 4, 'config_qr', 'Configuración y generación de códigos QR de asistencia', 'asistencia', '#06b6d4', NULL, NULL, 'MODULO', 0, NULL, NULL, NULL, NULL, NULL, true, true, false, true, '2026-10-12 08:59:08.662218', '2026-10-12 08:59:08.662218'),
	(6, 'Tareas', '/tareas', 'clipboard-outline', true, 5, 'tareas', 'Gestión de tareas y asignaciones del equipo', 'gestion', '#f59e0b', NULL, NULL, 'MODULO', 0, NULL, NULL, NULL, NULL, NULL, true, true, false, true, '2026-10-12 08:59:08.662218', '2026-10-12 08:59:08.662218'),
	(7, 'Inventario', '/inventario', 'cube-outline', true, 6, 'inventario', 'Control de inventario y recursos del sistema', 'gestion', '#10b981', NULL, NULL, 'MODULO', 0, NULL, NULL, NULL, NULL, NULL, true, true, false, true, '2026-10-12 08:59:08.662218', '2026-10-12 08:59:08.662218'),
	(12, 'Grupos', '/grupos', 'business-outline', true, 7, NULL, NULL, 'principal', NULL, NULL, NULL, 'MODULO', 0, NULL, NULL, NULL, NULL, NULL, true, true, false, true, '2026-10-12 08:59:09.450699', '2026-10-12 08:59:09.450699'),
	(8, 'Documentos', '/documentos', 'folder-outline', true, 8, 'documentos', 'Gestión y almacenamiento de documentos institucionales', 'documentos', '#f97316', NULL, NULL, 'MODULO', 0, NULL, NULL, NULL, NULL, NULL, true, true, false, true, '2026-10-12 08:59:08.662218', '2026-10-12 08:59:08.662218'),
	(9, 'Reporte', '/reporte', 'bar-chart-outline', true, 9, 'reportes', 'Generación y exportación de reportes del sistema', 'reportes', '#ef4444', NULL, NULL, 'MODULO', 0, NULL, NULL, NULL, NULL, NULL, true, true, false, true, '2026-10-12 08:59:08.662218', '2026-10-12 08:59:08.662218'),
	(10, 'Configuración', '/config', 'settings-outline', true, 10, 'configuracion', 'Configuración general del sistema, usuarios y seguridad', 'sistema', '#64748b', NULL, NULL, 'MODULO', 0, NULL, NULL, NULL, NULL, NULL, true, true, false, true, '2026-10-12 08:59:08.662218', '2026-10-12 08:59:08.662218'),
	(14, 'Notas', '/notas', 'reader-outline', true, 11, 'notas', 'Control de consecutivos, PDF oficial y seguimiento de notas institucionales', 'gestion', '#0f766e', NULL, '/api/notas', 'MODULO', 0, NULL, NULL, NULL, NULL, NULL, false, true, true, true, '2026-10-12 08:59:11.108282', '2026-10-12 08:59:11.108282'),
	(15, 'Memos', '/memos', 'mail-outline', true, 12, 'memos', 'Control de consecutivos y seguimiento de memorandos institucionales', 'gestion', '#2563eb', NULL, '/api/memos', 'MODULO', 0, NULL, NULL, NULL, NULL, NULL, false, true, true, true, '2026-10-12 08:59:11.443718', '2026-10-12 08:59:11.443718'),
	(16, 'Correspondencia', '/correspondencia', 'mail-outline', true, 13, 'correspondencia', 'Acceso unificado a notas y memos institucionales', 'gestion', '#2563eb', NULL, '/api/notas,/api/memos', 'MODULO', 0, NULL, NULL, NULL, NULL, NULL, true, true, true, true, '2026-10-12 08:59:11.558371', '2026-10-12 08:59:11.558371');


--
-- Data for Name: nota_historial; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: nota_numeros; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: nota_pdfs; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: nota_relaciones; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: notas; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: notificaciones; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: notification_channels; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: notification_events; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: notification_global_settings; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: notification_history; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: notification_recipients; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: notification_settings; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: notification_templates; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: org_units; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.org_units (id, nombre, siglas, nivel, padre_id, responsable, activo, codigo, descripcion, tipo, telefono, correo, orden, created_at, updated_at) VALUES
	(1, 'SIGAA', 'SIGAA', 0, NULL, NULL, false, NULL, NULL, 'Unidad', NULL, NULL, 0, '2026-10-12 08:59:08.866086', '2026-10-12 08:59:08.866086'),
	(17, 'Dirección', 'DIR', 0, NULL, NULL, true, NULL, NULL, 'Unidad', NULL, NULL, 1, '2026-10-12 08:59:09.771928', '2026-10-12 08:59:09.771928'),
	(2, 'Dirección General', 'DG', 1, 1, 'Carlos López', false, NULL, NULL, 'Unidad', NULL, NULL, 0, '2026-10-12 08:59:08.866086', '2026-10-12 08:59:08.866086'),
	(3, 'Secretaría General', 'SEC', 1, 1, 'María García', false, NULL, NULL, 'Unidad', NULL, NULL, 0, '2026-10-12 08:59:08.866086', '2026-10-12 08:59:08.866086'),
	(8, 'Subdirección', 'SUB', 1, 17, NULL, true, NULL, NULL, 'Unidad', NULL, NULL, 3, '2026-10-12 08:59:09.730581', '2026-10-12 08:59:09.730581'),
	(9, 'Secretaría', 'SEC', 1, 17, NULL, true, NULL, NULL, 'Unidad', NULL, NULL, 1, '2026-10-12 08:59:09.730581', '2026-10-12 08:59:09.730581'),
	(10, 'Asesoría Legal', 'AL', 1, 17, NULL, true, NULL, NULL, 'Unidad', NULL, NULL, 2, '2026-10-12 08:59:09.730581', '2026-10-12 08:59:09.730581'),
	(4, 'Departamento TI', 'TI', 2, 2, 'Pedro Ramírez', false, NULL, NULL, 'Unidad', NULL, NULL, 0, '2026-10-12 08:59:08.866086', '2026-10-12 08:59:08.866086'),
	(5, 'Departamento Legal', 'LEG', 2, 2, 'Ana Martínez', false, NULL, NULL, 'Unidad', NULL, NULL, 0, '2026-10-12 08:59:08.866086', '2026-10-12 08:59:08.866086'),
	(6, 'Atención al Ciudadano', 'ATE', 2, 3, NULL, false, NULL, NULL, 'Unidad', NULL, NULL, 0, '2026-10-12 08:59:08.866086', '2026-10-12 08:59:08.866086'),
	(7, 'Archivo Central', 'ARC', 2, 3, NULL, false, NULL, NULL, 'Unidad', NULL, NULL, 0, '2026-10-12 08:59:08.866086', '2026-10-12 08:59:08.866086'),
	(11, 'Unidad de Operaciones', 'UOP', 2, 8, NULL, true, NULL, NULL, 'Unidad', NULL, NULL, 1, '2026-10-12 08:59:09.730581', '2026-10-12 08:59:09.730581'),
	(12, 'Unidad de Comunicación Digital', 'UCD', 2, 8, NULL, true, NULL, NULL, 'Unidad', NULL, NULL, 2, '2026-10-12 08:59:09.730581', '2026-10-12 08:59:09.730581'),
	(13, 'Unidad de Análisis y Estadística', 'UAE', 2, 8, NULL, true, NULL, NULL, 'Unidad', NULL, NULL, 3, '2026-10-12 08:59:09.730581', '2026-10-12 08:59:09.730581'),
	(14, 'Unidad de Tecnología y Estructura', 'UTE', 2, 8, NULL, true, NULL, NULL, 'Unidad', NULL, NULL, 4, '2026-10-12 08:59:09.730581', '2026-10-12 08:59:09.730581'),
	(15, 'Unidad de Comunicación Estratégica', 'UCE', 2, 8, NULL, true, NULL, NULL, 'Unidad', NULL, NULL, 5, '2026-10-12 08:59:09.730581', '2026-10-12 08:59:09.730581'),
	(16, 'Unidad de Gestión Administrativa', 'UGA', 2, 8, NULL, true, NULL, NULL, 'Unidad', NULL, NULL, 6, '2026-10-12 08:59:09.730581', '2026-10-12 08:59:09.730581');



--
-- Data for Name: organization_departments; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: organization_settings; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.organization_settings (id, organization_id, clave, valor, descripcion, created_at, updated_at) VALUES
	(1, 1, 'tema', 'claro', 'Tema visual del sistema', '2026-10-12 08:59:08.866086', '2026-10-12 08:59:08.866086'),
	(2, 1, 'animaciones', 'true', 'Habilitar animaciones de interfaz', '2026-10-12 08:59:08.866086', '2026-10-12 08:59:08.866086'),
	(3, 1, 'items_por_pagina', '20', 'Elementos por pagina en listas', '2026-10-12 08:59:08.866086', '2026-10-12 08:59:08.866086'),
	(4, 1, 'formato_fecha', 'dd/MM/yyyy', 'Formato de fecha del sistema', '2026-10-12 08:59:08.866086', '2026-10-12 08:59:08.866086'),
	(5, 1, 'formato_hora', 'HH:mm', 'Formato de hora del sistema', '2026-10-12 08:59:08.866086', '2026-10-12 08:59:08.866086');



--
-- Data for Name: organizations; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.organizations (id, nombre, siglas, codigo_institucional, tipo_organizacion, ruc, director, estado, activo, created_at, updated_at, director_cargo) VALUES
	(1, 'Sistema de Gestion de Actas y Asistencia', 'SIGAA', 'SIGAA-001', 'Institucion', NULL, 'PruebasJr', 'ACTIVO', true, '2026-10-12 08:59:08.866086', '2026-10-23 08:22:56.769855', 'Director'),
	(17, 'Universidad Tecnologica de Panama', 'UTP', NULL, NULL, NULL, 'Jr.', 'ACTIVO', true, '2026-10-23 08:24:48.455161', '2026-10-23 08:24:48.455161', 'Director General');



--
-- Data for Name: parameter_history; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: parametros; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: password_history; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: password_reset_requests; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: permisos; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.permisos (id, modulo, accion, recurso, descripcion, activo) VALUES
	(11, 'Panel', 'Ver', 'Dashboard', 'Acceder al panel principal', true),
	(12, 'Panel', 'Gestionar usuarios', 'Dashboard', 'Gestionar usuarios desde el dashboard', true),
	(13, 'Panel', 'Gestionar roles', 'Dashboard', 'Gestionar roles desde el dashboard', true),
	(14, 'Panel', 'Gestionar inventario', 'Dashboard', 'Gestionar inventario desde el dashboard', true),
	(15, 'Panel', 'Gestionar reuniones', 'Dashboard', 'Gestionar reuniones desde el dashboard', true),
	(16, 'Panel', 'Gestionar actas', 'Dashboard', 'Gestionar actas desde el dashboard', true),
	(17, 'Panel', 'Gestionar asistencia', 'Dashboard', 'Gestionar asistencia desde el dashboard', true),
	(18, 'Panel', 'Gestionar documentos', 'Dashboard', 'Gestionar documentos desde el dashboard', true),
	(19, 'Panel', 'Gestionar configuracion', 'Dashboard', 'Gestionar configuracion desde el dashboard', true),
	(20, 'Panel', 'Generar reportes', 'Dashboard', 'Generar reportes desde el dashboard', true),
	(21, 'Reuniones', 'Ver', 'Reunion', 'Ver lista de reuniones', true),
	(22, 'Reuniones', 'Crear', 'Reunion', 'Crear nuevas reuniones', true),
	(23, 'Reuniones', 'Editar', 'Reunion', 'Editar reuniones existentes', true),
	(24, 'Reuniones', 'Eliminar', 'Reunion', 'Eliminar reuniones', true),
	(25, 'Reuniones', 'Administrar', 'Reunion', 'Administrar reuniones del sistema', true),
	(26, 'Reuniones', 'Asignar', 'Reunion', 'Asignar participantes a reuniones', true),
	(27, 'Reuniones', 'Cerrar', 'Reunion', 'Cerrar reuniones', true),
	(28, 'Reuniones', 'Abrir', 'Reunion', 'Abrir reuniones', true),
	(29, 'Reuniones', 'Cambiar estado', 'Reunion', 'Cambiar el estado de una reunion', true),
	(30, 'Reuniones', 'Exportar', 'Reunion', 'Exportar datos de reuniones', true),
	(31, 'Reuniones', 'Imprimir', 'Reunion', 'Imprimir detalles de reunion', true),
	(32, 'Actas', 'Ver', 'Acta', 'Ver lista de actas', true),
	(33, 'Actas', 'Crear', 'Acta', 'Crear nuevas actas', true),
	(34, 'Actas', 'Editar', 'Acta', 'Editar actas existentes', true),
	(35, 'Actas', 'Eliminar', 'Acta', 'Eliminar actas', true),
	(36, 'Actas', 'Aprobar', 'Acta', 'Aprobar actas', true),
	(37, 'Actas', 'Rechazar', 'Acta', 'Rechazar actas', true),
	(38, 'Actas', 'Firmar', 'Acta', 'Firmar actas', true),
	(39, 'Actas', 'Autorizar', 'Acta', 'Autorizar actas', true),
	(40, 'Actas', 'Cambiar estado', 'Acta', 'Cambiar el estado de un acta', true),
	(41, 'Actas', 'Exportar', 'Acta', 'Exportar actas a diferentes formatos', true),
	(42, 'Actas', 'Imprimir', 'Acta', 'Imprimir actas', true),
	(43, 'Actas', 'Descargar', 'Acta', 'Descargar archivos de actas', true),
	(44, 'Actas', 'Subir archivos', 'Acta', 'Subir adjuntos a actas', true),
	(45, 'Actas', 'Administrar', 'Acta', 'Administrar actas del sistema', true),
	(46, 'Actas', 'Gestionar actas', 'Acta', 'Gestionar el ciclo de vida de actas', true),
	(47, 'Configuracion QR', 'Ver', 'Sesion', 'Ver sesiones QR', true),
	(48, 'Configuracion QR', 'Crear', 'Sesion', 'Crear sesiones QR', true),
	(49, 'Configuracion QR', 'Editar', 'Sesion', 'Editar sesiones QR', true),
	(50, 'Configuracion QR', 'Eliminar', 'Sesion', 'Eliminar sesiones QR', true),
	(51, 'Configuracion QR', 'Administrar', 'Sesion', 'Administrar sesiones QR', true),
	(52, 'Configuracion QR', 'Cerrar', 'Sesion', 'Cerrar sesiones QR', true),
	(53, 'Configuracion QR', 'Abrir', 'Sesion', 'Abrir sesiones QR', true),
	(54, 'Configuracion QR', 'Configurar', 'Sesion', 'Configurar parametros de sesiones QR', true),
	(55, 'Configuracion QR', 'Exportar', 'Sesion', 'Exportar datos de asistencia QR', true),
	(65, 'Tareas', 'Ver', 'Tarea', 'Ver lista de tareas', true),
	(66, 'Tareas', 'Crear', 'Tarea', 'Crear nuevas tareas', true),
	(67, 'Tareas', 'Editar', 'Tarea', 'Editar tareas existentes', true),
	(68, 'Tareas', 'Eliminar', 'Tarea', 'Eliminar tareas', true),
	(69, 'Tareas', 'Asignar', 'Tarea', 'Asignar tareas a usuarios', true),
	(70, 'Tareas', 'Cambiar estado', 'Tarea', 'Cambiar estado de tareas', true),
	(71, 'Tareas', 'Administrar', 'Tarea', 'Administrar tareas del sistema', true),
	(72, 'Tareas', 'Exportar', 'Tarea', 'Exportar datos de tareas', true),
	(73, 'Tareas', 'Descargar', 'Tarea', 'Descargar archivos de tareas', true),
	(74, 'Tareas', 'Subir archivos', 'Tarea', 'Subir adjuntos a tareas', true),
	(75, 'Inventario', 'Ver', 'Inventario', 'Ver inventario', true),
	(76, 'Inventario', 'Crear', 'Inventario', 'Crear items de inventario', true),
	(77, 'Inventario', 'Editar', 'Inventario', 'Editar items de inventario', true),
	(78, 'Inventario', 'Eliminar', 'Inventario', 'Eliminar items de inventario', true),
	(79, 'Inventario', 'Administrar', 'Inventario', 'Administrar inventario del sistema', true),
	(80, 'Inventario', 'Gestionar inventario', 'Inventario', 'Gestionar el ciclo de vida del inventario', true),
	(81, 'Inventario', 'Exportar', 'Inventario', 'Exportar datos de inventario', true),
	(82, 'Inventario', 'Imprimir', 'Inventario', 'Imprimir reportes de inventario', true),
	(83, 'Inventario', 'Generar reportes', 'Inventario', 'Generar reportes de inventario', true),
	(84, 'Inventario', 'Cambiar estado', 'Inventario', 'Cambiar estado de items de inventario', true),
	(85, 'Documentos', 'Ver', 'Documento', 'Ver documentos', true),
	(86, 'Documentos', 'Crear', 'Documento', 'Crear nuevos documentos', true),
	(87, 'Documentos', 'Editar', 'Documento', 'Editar documentos existentes', true),
	(88, 'Documentos', 'Eliminar', 'Documento', 'Eliminar documentos', true),
	(89, 'Documentos', 'Subir archivos', 'Documento', 'Subir archivos de documentos', true),
	(90, 'Documentos', 'Descargar', 'Documento', 'Descargar documentos', true),
	(91, 'Documentos', 'Administrar', 'Documento', 'Administrar documentos del sistema', true),
	(92, 'Documentos', 'Gestionar documentos', 'Documento', 'Gestionar el ciclo de vida de documentos', true),
	(93, 'Documentos', 'Exportar', 'Documento', 'Exportar documentos', true),
	(94, 'Documentos', 'Imprimir', 'Documento', 'Imprimir documentos', true),
	(95, 'Documentos', 'Aprobar', 'Documento', 'Aprobar documentos', true),
	(96, 'Documentos', 'Rechazar', 'Documento', 'Rechazar documentos', true),
	(97, 'Documentos', 'Firmar', 'Documento', 'Firmar documentos digitalmente', true),
	(98, 'Documentos', 'Autorizar', 'Documento', 'Autorizar documentos', true),
	(99, 'Documentos', 'Cambiar estado', 'Documento', 'Cambiar estado de documentos', true),
	(100, 'Reporte', 'Ver', 'Reporte', 'Ver reportes', true),
	(101, 'Reporte', 'Crear', 'Reporte', 'Crear nuevos reportes', true),
	(102, 'Reporte', 'Editar', 'Reporte', 'Editar reportes existentes', true),
	(103, 'Reporte', 'Eliminar', 'Reporte', 'Eliminar reportes', true),
	(104, 'Reporte', 'Generar reportes', 'Reporte', 'Generar reportes del sistema', true),
	(105, 'Reporte', 'Exportar', 'Reporte', 'Exportar reportes', true),
	(106, 'Reporte', 'Descargar', 'Reporte', 'Descargar archivos de reportes', true),
	(107, 'Reporte', 'Subir archivos', 'Reporte', 'Subir archivos a reportes', true),
	(108, 'Reporte', 'Administrar', 'Reporte', 'Administrar reportes del sistema', true),
	(109, 'Reporte', 'Imprimir', 'Reporte', 'Imprimir reportes', true),
	(110, 'Reporte', 'Configurar', 'Reporte', 'Configurar parametros de reportes', true),
	(118, 'Configuración', 'Ver', 'Permiso', 'Ver permisos del sistema', true),
	(119, 'Configuración', 'Crear', 'Permiso', 'Crear nuevos permisos', true),
	(120, 'Configuración', 'Editar', 'Permiso', 'Editar permisos existentes', true),
	(121, 'Configuración', 'Eliminar', 'Permiso', 'Eliminar permisos del sistema', true),
	(122, 'Configuración', 'Administrar', 'Permiso', 'Administrar matriz de permisos', true),
	(123, 'Configuración', 'Ver', 'Seguridad', 'Ver configuracion de seguridad', true),
	(124, 'Configuración', 'Configurar', 'Seguridad', 'Configurar parametros de seguridad', true),
	(125, 'Configuración', 'Administrar', 'Seguridad', 'Administrar la seguridad del sistema', true),
	(126, 'Configuración', 'Ver', 'Notificacion', 'Ver configuracion de notificaciones', true);
INSERT INTO public.permisos (id, modulo, accion, recurso, descripcion, activo) VALUES
	(127, 'Configuración', 'Configurar', 'Notificacion', 'Configurar notificaciones del sistema', true),
	(128, 'Configuración', 'Administrar', 'Notificacion', 'Administrar notificaciones del sistema', true),
	(129, 'Configuración', 'Ver', 'Bitacora', 'Ver registros de auditoria', true),
	(130, 'Configuración', 'Administrar', 'Bitacora', 'Administrar registros de auditoria', true),
	(131, 'Configuración', 'Exportar', 'Bitacora', 'Exportar registros de auditoria', true),
	(132, 'Configuración', 'Ver', 'Respaldo', 'Ver respaldos del sistema', true),
	(133, 'Configuración', 'Crear', 'Respaldo', 'Crear respaldos del sistema', true),
	(134, 'Configuración', 'Eliminar', 'Respaldo', 'Eliminar respaldos del sistema', true),
	(135, 'Configuración', 'Administrar', 'Respaldo', 'Administrar respaldos del sistema', true),
	(136, 'Configuración', 'Ver', 'Parametro', 'Ver parametros del sistema', true),
	(137, 'Configuración', 'Editar', 'Parametro', 'Editar parametros del sistema', true),
	(138, 'Configuración', 'Administrar', 'Parametro', 'Administrar parametros del sistema', true),
	(139, 'Configuración', 'Ver', 'Catalogo', 'Ver catalogos del sistema', true),
	(140, 'Configuración', 'Editar', 'Catalogo', 'Editar catalogos del sistema', true),
	(141, 'Configuración', 'Administrar', 'Catalogo', 'Administrar catalogos del sistema', true),
	(142, 'Configuración', 'Ver', 'Organizacion', 'Ver estructura organizacional', true),
	(143, 'Configuración', 'Editar', 'Organizacion', 'Editar estructura organizacional', true),
	(144, 'Configuración', 'Administrar', 'Organizacion', 'Administrar la estructura organizacional', true),
	(145, 'Configuración', 'Ver', 'Ubicacion', 'Ver ubicaciones del sistema', true),
	(146, 'Configuración', 'Crear', 'Ubicacion', 'Crear ubicaciones', true),
	(147, 'Configuración', 'Editar', 'Ubicacion', 'Editar ubicaciones', true),
	(148, 'Configuración', 'Eliminar', 'Ubicacion', 'Eliminar ubicaciones', true),
	(149, 'Configuración', 'Administrar', 'Ubicacion', 'Administrar ubicaciones', true),
	(111, 'Configuración', 'Acceder', 'Configuración', 'Acceso al modulo de configuracion', true),
	(112, 'Configuración', 'Ver', 'Configuración', 'Ver configuracion del sistema', true),
	(113, 'Configuración', 'Configurar', 'Configuración', 'Modificar configuracion del sistema', true),
	(114, 'Configuración', 'Administrar', 'Configuración', 'Administrar configuracion del sistema', true),
	(115, 'Configuración', 'Gestionar usuarios', 'Configuración', 'Gestionar usuarios desde configuracion', true),
	(116, 'Configuración', 'Gestionar roles', 'Configuración', 'Gestionar roles desde configuracion', true),
	(117, 'Configuración', 'Gestionar configuracion', 'Configuración', 'Gestionar la configuracion global', true),
	(159, 'Dashboard', 'Ver', 'Dashboard', 'Ver el dashboard de configuración', true),
	(160, 'Usuarios', 'Ver', 'Usuario', 'Ver lista de usuarios', true),
	(161, 'Roles', 'Ver', 'Rol', 'Ver lista de roles', true),
	(162, 'Reuniones', 'Iniciar', 'Reunion', 'Iniciar reuniones', true),
	(163, 'Documentos', 'Crear carpeta', 'Carpeta', 'Crear carpetas de documentos', true),
	(164, 'Reuniones', 'Duplicar', 'Reunion', 'Duplicar reuniones', true),
	(165, 'Reuniones', 'Ver tareas', 'Reunion', 'Ver tareas relacionadas con reuniones', true),
	(166, 'Reuniones', 'Gestionar participantes', 'Reunion', 'Gestionar participantes de reuniones', true),
	(167, 'Tareas', 'Importar', 'Tarea', 'Importar tareas', true),
	(168, 'Reuniones', 'Gestionar tareas', 'Reunion', 'Gestionar tareas relacionadas con reuniones', true),
	(169, 'Tareas', 'Completar', 'Tarea', 'Marcar tareas como completadas', true),
	(170, 'Actas', 'Copiar enlace', 'Acta', 'Copiar el enlace de un acta', true),
	(171, 'Reuniones', 'Generar acta', 'Reunion', 'Generar actas desde reuniones', true),
	(172, 'Actas', 'Enviar correo', 'Acta', 'Enviar un acta por correo', true),
	(173, 'Reuniones', 'Cancelar', 'Reunion', 'Cancelar reuniones', true),
	(174, 'Grupos', 'Editar', 'Grupo', 'Editar grupos de trabajo', true),
	(175, 'Grupos', 'Eliminar', 'Grupo', 'Eliminar grupos de trabajo', true),
	(176, 'Grupos', 'Crear', 'Grupo', 'Crear grupos de trabajo', true),
	(177, 'Grupos', 'Ver actividades', 'Grupo', 'Consultar actividades del grupo', true),
	(178, 'Grupos', 'Ver', 'Grupo', 'Consultar grupos de trabajo', true),
	(179, 'Grupos', 'Cambiar estado', 'Grupo', 'Activar o desactivar grupos', true),
	(180, 'Grupos', 'Gestionar miembros', 'Grupo', 'Administrar miembros de grupos', true),
	(181, 'Grupos', 'Asignar permisos', 'Grupo', 'Asignar permisos relacionados al grupo', true),
	(188, 'Usuarios', 'Crear', 'Usuario', 'Crear cuentas de usuario', true),
	(189, 'Usuarios', 'Eliminar', 'Usuario', 'Eliminar cuentas de usuario', true),
	(190, 'Usuarios', 'Editar', 'Usuario', 'Actualizar cuentas de usuario', true),
	(191, 'Notas', 'Descargar', 'Nota', 'Abrir el PDF oficial de una nota', true),
	(192, 'Notas', 'Cerrar', 'Nota', 'Cerrar notas firmadas', true),
	(193, 'Notas', 'Ver', 'Nota', 'Consultar notas y consecutivos', true),
	(194, 'Notas', 'Firmar', 'Nota', 'Marcar notas como firmadas', true),
	(195, 'Notas', 'Gestionar números', 'NotaNumero', 'Cargar consecutivos de notas al sistema', true),
	(196, 'Notas', 'Crear', 'Nota', 'Crear una nota y reservar un consecutivo', true),
	(197, 'Notas', 'Editar', 'Nota', 'Editar notas en seguimiento', true),
	(198, 'Notas', 'Reservar número', 'NotaNumero', 'Reservar y liberar temporalmente consecutivos de notas', true),
	(199, 'Notas', 'Relacionar', 'NotaRelacion', 'Crear y eliminar relaciones entre notas', true),
	(200, 'Notas', 'Adjuntar PDF', 'NotaPdf', 'Adjuntar y versionar el PDF oficial de una nota', true),
	(201, 'Notas', 'Anular', 'Nota', 'Anular una nota indicando un motivo obligatorio', true),
	(202, 'Notas', 'Eliminar PDF', 'NotaPdf', 'Retirar una versión PDF indicando un motivo obligatorio', true),
	(203, 'Memos', 'Ver', 'Memo', 'Consultar memos y su trazabilidad', true),
	(204, 'Memos', 'Crear', 'Memo', 'Registrar memos utilizando un consecutivo reservado', true),
	(205, 'Memos', 'Gestionar números', 'MemoNumero', 'Cargar consecutivos de memos', true),
	(206, 'Memos', 'Completar', 'Memo', 'Marcar memos como completados', true),
	(207, 'Memos', 'Anular', 'Memo', 'Anular memos indicando un motivo obligatorio', true),
	(208, 'Memos', 'Dar seguimiento', 'Memo', 'Agregar actualizaciones al seguimiento de un memo', true),
	(209, 'Memos', 'Reservar número', 'MemoNumero', 'Reservar temporalmente consecutivos de memos', true),
	(210, 'Correspondencia', 'Ver', 'Correspondencia', 'Acceder a la sección unificada de notas y memos', true),
	(211, 'Notas', 'Dar seguimiento', 'Nota', 'Agregar comentarios a la trazabilidad de una nota firmada', true),
	(212, 'Notas', 'Cambiar seguimiento', 'Nota', 'Registrar entrega, recepción y respuesta de una nota firmada', true);


--
-- Data for Name: reporte_programaciones; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: reportes; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: reunion_participantes_usuario; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: reunion_unidades; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: reuniones; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: rol_permisos; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.rol_permisos (id, rol_id, permiso_id) VALUES
	(1223, 8, 11),
	(1224, 9, 11),
	(1225, 8, 21),
	(1226, 9, 21),
	(1227, 8, 22),
	(1228, 9, 22),
	(1229, 8, 23),
	(1230, 9, 23),
	(1231, 8, 24),
	(1232, 9, 24),
	(1233, 8, 26),
	(1234, 9, 26),
	(1235, 8, 27),
	(1236, 9, 27),
	(1237, 8, 28),
	(1238, 9, 28),
	(1239, 8, 29),
	(1240, 9, 29),
	(1241, 8, 30),
	(1242, 9, 30),
	(1243, 8, 31),
	(1244, 9, 31),
	(1245, 8, 32),
	(1246, 9, 32),
	(1247, 8, 33),
	(1248, 9, 33),
	(1249, 8, 34),
	(1250, 9, 34),
	(1251, 8, 35),
	(1252, 9, 35),
	(1253, 8, 36),
	(1254, 9, 36),
	(1255, 8, 37),
	(1256, 9, 37),
	(1257, 8, 38),
	(1258, 9, 38),
	(1259, 8, 39),
	(1260, 9, 39),
	(1261, 8, 40),
	(1262, 9, 40),
	(1263, 8, 41),
	(1264, 9, 41),
	(1265, 8, 42),
	(1266, 9, 42),
	(1267, 8, 43),
	(1268, 9, 43),
	(1269, 8, 44),
	(1270, 9, 44),
	(1271, 8, 46),
	(1272, 9, 46),
	(1273, 8, 65),
	(1274, 9, 65),
	(1275, 8, 66),
	(1276, 9, 66),
	(1277, 8, 67),
	(1278, 9, 67),
	(1279, 8, 68),
	(1280, 9, 68),
	(1281, 8, 69),
	(1282, 9, 69),
	(1283, 8, 70),
	(1284, 9, 70),
	(1285, 8, 72),
	(1286, 9, 72),
	(1287, 8, 73),
	(1288, 9, 73),
	(1289, 8, 74),
	(1290, 9, 74),
	(1291, 8, 75),
	(1292, 9, 75),
	(1293, 8, 76),
	(1294, 9, 76),
	(1295, 8, 77),
	(1296, 9, 77),
	(1297, 8, 78),
	(1298, 9, 78),
	(1299, 8, 80),
	(1300, 9, 80),
	(1301, 8, 81),
	(1302, 9, 81),
	(1303, 8, 82),
	(1304, 9, 82),
	(1305, 8, 83),
	(1306, 9, 83),
	(1307, 8, 84),
	(1308, 9, 84),
	(1309, 8, 85),
	(1310, 9, 85),
	(1311, 8, 86),
	(1312, 9, 86),
	(1313, 8, 87),
	(1314, 9, 87),
	(1315, 8, 88),
	(1316, 9, 88),
	(1317, 8, 89),
	(1318, 9, 89),
	(1319, 8, 90),
	(1320, 9, 90),
	(1321, 8, 92),
	(1322, 9, 92);
INSERT INTO public.rol_permisos (id, rol_id, permiso_id) VALUES
	(1323, 8, 93),
	(1324, 9, 93),
	(1325, 8, 94),
	(1326, 9, 94),
	(1327, 8, 95),
	(1328, 9, 95),
	(1329, 8, 96),
	(1330, 9, 96),
	(1331, 8, 97),
	(1332, 9, 97),
	(1333, 8, 98),
	(1334, 9, 98),
	(1335, 8, 99),
	(1336, 9, 99),
	(1337, 8, 100),
	(1338, 9, 100),
	(1339, 8, 101),
	(1340, 9, 101),
	(1341, 8, 102),
	(1342, 9, 102),
	(1343, 8, 103),
	(1344, 9, 103),
	(1345, 8, 104),
	(1346, 9, 104),
	(1347, 8, 105),
	(1348, 9, 105),
	(1349, 8, 106),
	(1350, 9, 106),
	(1351, 8, 107),
	(1352, 9, 107),
	(1353, 8, 109),
	(1354, 9, 109),
	(1355, 8, 162),
	(1356, 9, 162),
	(1357, 8, 163),
	(1358, 9, 163),
	(1359, 8, 164),
	(1360, 9, 164),
	(1361, 8, 165),
	(1362, 9, 165),
	(1363, 8, 166),
	(1364, 9, 166),
	(1365, 8, 167),
	(1366, 9, 167),
	(1367, 8, 168),
	(1368, 9, 168),
	(1369, 8, 169),
	(1370, 9, 169),
	(1371, 8, 170),
	(1372, 9, 170),
	(1373, 8, 171),
	(1374, 9, 171),
	(1375, 8, 172),
	(1376, 9, 172),
	(1377, 8, 173),
	(1378, 9, 173),
	(1379, 8, 174),
	(1380, 9, 174),
	(316, 4, 11),
	(1381, 8, 175),
	(1382, 9, 175),
	(1383, 8, 176),
	(1384, 9, 176),
	(1385, 8, 177),
	(1386, 9, 177),
	(1387, 8, 178),
	(1388, 9, 178),
	(1389, 8, 179),
	(1390, 9, 179),
	(1391, 8, 180),
	(1392, 9, 180),
	(1393, 8, 181),
	(1394, 9, 181),
	(1395, 8, 191),
	(1396, 9, 191),
	(1397, 8, 192),
	(1398, 9, 192),
	(1399, 8, 193),
	(1400, 9, 193),
	(1401, 8, 194),
	(1402, 9, 194),
	(1403, 8, 195),
	(1404, 9, 195),
	(1405, 8, 196),
	(1406, 9, 196),
	(1407, 8, 197),
	(1408, 9, 197),
	(1409, 8, 198),
	(1410, 9, 198),
	(1411, 8, 199),
	(1412, 9, 199),
	(1413, 8, 200),
	(1414, 9, 200),
	(1415, 8, 201),
	(1416, 9, 201),
	(1417, 8, 202),
	(1418, 9, 202),
	(1419, 8, 203),
	(1420, 9, 203),
	(1421, 8, 204);
INSERT INTO public.rol_permisos (id, rol_id, permiso_id) VALUES
	(1422, 9, 204),
	(1423, 8, 205),
	(1424, 9, 205),
	(1425, 8, 206),
	(1426, 9, 206),
	(1427, 8, 207),
	(1428, 9, 207),
	(1429, 8, 208),
	(1430, 9, 208),
	(1431, 8, 209),
	(1432, 9, 209),
	(1433, 10, 11),
	(1434, 10, 32),
	(1435, 10, 42),
	(1436, 10, 43),
	(1437, 10, 85),
	(1438, 10, 86),
	(1439, 10, 87),
	(1440, 10, 88),
	(1441, 10, 89),
	(1442, 10, 90),
	(1443, 10, 92),
	(1444, 10, 93),
	(1445, 10, 94),
	(1446, 10, 95),
	(1447, 10, 96),
	(1448, 10, 97),
	(1449, 10, 98),
	(1450, 10, 99),
	(1451, 10, 163),
	(1452, 10, 170),
	(1453, 10, 191),
	(1454, 10, 192),
	(1455, 10, 193),
	(1456, 10, 194),
	(1457, 10, 195),
	(1458, 10, 196),
	(1459, 10, 197),
	(1460, 10, 198),
	(1461, 10, 199),
	(1462, 10, 200),
	(1463, 10, 201),
	(1464, 10, 202),
	(1465, 10, 203),
	(1466, 10, 204),
	(1467, 10, 205),
	(1468, 10, 206),
	(1469, 10, 207),
	(1470, 10, 208),
	(1471, 10, 209),
	(1472, 1, 211),
	(1473, 2, 211),
	(1474, 3, 211),
	(1475, 5, 211),
	(1476, 8, 211),
	(1477, 9, 211),
	(1478, 10, 211),
	(1479, 1, 212),
	(1480, 2, 212),
	(1481, 5, 212),
	(1482, 8, 212),
	(1483, 9, 212),
	(1484, 10, 212),
	(1500, 8, 25),
	(1501, 9, 25),
	(1540, 8, 45),
	(1541, 9, 45),
	(1544, 8, 47),
	(1545, 9, 47),
	(1546, 8, 48),
	(1547, 9, 48),
	(1548, 8, 49),
	(1549, 9, 49),
	(1550, 8, 50),
	(1551, 9, 50),
	(1552, 8, 51),
	(1553, 9, 51),
	(1554, 8, 52),
	(1555, 9, 52),
	(1556, 8, 53),
	(1557, 9, 53),
	(1558, 8, 54),
	(1559, 9, 54),
	(1560, 8, 55),
	(1561, 9, 55),
	(1574, 8, 71),
	(1575, 9, 71),
	(1590, 8, 79),
	(1591, 9, 79),
	(1614, 8, 91),
	(1615, 9, 91),
	(1648, 8, 108),
	(1649, 9, 108),
	(1652, 8, 110),
	(1653, 9, 110),
	(786, 1, 11),
	(787, 1, 12),
	(788, 1, 13),
	(789, 1, 14),
	(790, 1, 15);
INSERT INTO public.rol_permisos (id, rol_id, permiso_id) VALUES
	(791, 1, 16),
	(792, 1, 17),
	(793, 1, 18),
	(794, 1, 19),
	(795, 1, 20),
	(796, 1, 21),
	(797, 1, 22),
	(798, 1, 23),
	(799, 1, 24),
	(800, 1, 25),
	(801, 1, 26),
	(802, 1, 27),
	(803, 1, 28),
	(804, 1, 29),
	(805, 1, 30),
	(806, 1, 31),
	(807, 1, 32),
	(808, 1, 33),
	(809, 1, 34),
	(810, 1, 35),
	(811, 1, 36),
	(812, 1, 37),
	(813, 1, 38),
	(814, 1, 39),
	(815, 1, 40),
	(816, 1, 41),
	(817, 1, 42),
	(818, 1, 43),
	(819, 1, 44),
	(820, 1, 45),
	(821, 1, 46),
	(822, 1, 47),
	(823, 1, 48),
	(824, 1, 49),
	(825, 1, 50),
	(826, 1, 51),
	(827, 1, 52),
	(828, 1, 53),
	(829, 1, 54),
	(830, 1, 55),
	(840, 1, 65),
	(841, 1, 66),
	(842, 1, 67),
	(843, 1, 68),
	(844, 1, 69),
	(845, 1, 70),
	(846, 1, 71),
	(847, 1, 72),
	(848, 1, 73),
	(849, 1, 74),
	(850, 1, 75),
	(851, 1, 76),
	(852, 1, 77),
	(853, 1, 78),
	(854, 1, 79),
	(855, 1, 80),
	(856, 1, 81),
	(857, 1, 82),
	(858, 1, 83),
	(859, 1, 84),
	(860, 1, 85),
	(861, 1, 86),
	(862, 1, 87),
	(863, 1, 88),
	(864, 1, 89),
	(865, 1, 90),
	(866, 1, 91),
	(867, 1, 92),
	(868, 1, 93),
	(869, 1, 94),
	(870, 1, 95),
	(871, 1, 96),
	(872, 1, 97),
	(873, 1, 98),
	(874, 1, 99),
	(875, 1, 100),
	(876, 1, 101),
	(877, 1, 102),
	(878, 1, 103),
	(879, 1, 104),
	(880, 1, 105),
	(881, 1, 106),
	(882, 1, 107),
	(883, 1, 108),
	(884, 1, 109),
	(885, 1, 110),
	(886, 1, 118),
	(887, 1, 119),
	(888, 1, 120),
	(889, 1, 121),
	(890, 1, 122),
	(891, 1, 123),
	(892, 1, 124),
	(893, 1, 125),
	(894, 1, 126),
	(895, 1, 127),
	(896, 1, 128),
	(897, 1, 129),
	(898, 1, 130),
	(899, 1, 131);
INSERT INTO public.rol_permisos (id, rol_id, permiso_id) VALUES
	(900, 1, 132),
	(901, 1, 133),
	(902, 1, 134),
	(903, 1, 135),
	(904, 1, 136),
	(905, 1, 137),
	(906, 1, 138),
	(907, 1, 139),
	(908, 1, 140),
	(909, 1, 141),
	(910, 1, 142),
	(911, 1, 143),
	(912, 1, 144),
	(913, 1, 145),
	(914, 1, 146),
	(915, 1, 147),
	(916, 1, 148),
	(917, 1, 149),
	(918, 1, 111),
	(919, 1, 112),
	(920, 1, 113),
	(921, 1, 114),
	(922, 1, 115),
	(923, 1, 116),
	(924, 1, 117),
	(925, 1, 159),
	(926, 1, 160),
	(927, 1, 161),
	(928, 1, 162),
	(929, 1, 163),
	(930, 1, 164),
	(931, 1, 165),
	(932, 1, 166),
	(933, 1, 167),
	(934, 1, 168),
	(935, 1, 169),
	(936, 1, 170),
	(937, 1, 171),
	(938, 1, 172),
	(939, 1, 173),
	(940, 1, 174),
	(941, 1, 175),
	(942, 1, 176),
	(943, 1, 177),
	(944, 1, 178),
	(945, 1, 179),
	(946, 1, 180),
	(947, 1, 181),
	(954, 5, 11),
	(955, 5, 12),
	(956, 5, 14),
	(957, 5, 15),
	(958, 5, 16),
	(959, 5, 17),
	(960, 5, 18),
	(961, 5, 20),
	(962, 5, 21),
	(963, 5, 22),
	(964, 5, 23),
	(965, 5, 24),
	(966, 5, 25),
	(967, 5, 26),
	(968, 5, 27),
	(969, 5, 28),
	(970, 5, 29),
	(971, 5, 30),
	(972, 5, 31),
	(973, 5, 32),
	(974, 5, 33),
	(975, 5, 34),
	(976, 5, 35),
	(977, 5, 36),
	(978, 5, 37),
	(979, 5, 38),
	(980, 5, 39),
	(981, 5, 40),
	(982, 5, 41),
	(983, 5, 42),
	(984, 5, 43),
	(985, 5, 44),
	(986, 5, 45),
	(987, 5, 46),
	(988, 5, 47),
	(989, 5, 48),
	(990, 5, 49),
	(991, 5, 50),
	(992, 5, 51),
	(993, 5, 52),
	(994, 5, 53),
	(995, 5, 54),
	(996, 5, 55),
	(1006, 5, 65),
	(1007, 5, 66),
	(1008, 5, 67),
	(1009, 5, 68),
	(1010, 5, 69),
	(1011, 5, 70),
	(1012, 5, 71),
	(1013, 5, 72),
	(1014, 5, 73);
INSERT INTO public.rol_permisos (id, rol_id, permiso_id) VALUES
	(1015, 5, 74),
	(1016, 5, 75),
	(1017, 5, 76),
	(1018, 5, 77),
	(1019, 5, 78),
	(1020, 5, 79),
	(1021, 5, 80),
	(1022, 5, 81),
	(1023, 5, 82),
	(1024, 5, 83),
	(1025, 5, 84),
	(1026, 5, 85),
	(1027, 5, 86),
	(1028, 5, 87),
	(1029, 5, 88),
	(1030, 5, 89),
	(1031, 5, 90),
	(1032, 5, 91),
	(1033, 5, 92),
	(1034, 5, 93),
	(1035, 5, 94),
	(1036, 5, 95),
	(1037, 5, 96),
	(1038, 5, 97),
	(1039, 5, 98),
	(1040, 5, 99),
	(1041, 5, 100),
	(1042, 5, 101),
	(1043, 5, 102),
	(1044, 5, 103),
	(1045, 5, 104),
	(1046, 5, 105),
	(1047, 5, 106),
	(1048, 5, 107),
	(1049, 5, 109),
	(1050, 5, 160),
	(1051, 5, 162),
	(1052, 5, 163),
	(1053, 5, 164),
	(1054, 5, 165),
	(1055, 5, 166),
	(1056, 5, 167),
	(1057, 5, 168),
	(1058, 5, 169),
	(1059, 5, 170),
	(1060, 5, 171),
	(1061, 5, 172),
	(1062, 5, 173),
	(1063, 5, 174),
	(1064, 5, 175),
	(1065, 5, 176),
	(1066, 5, 177),
	(1067, 5, 178),
	(1068, 5, 179),
	(1069, 5, 180),
	(1070, 5, 181),
	(1077, 2, 11),
	(1078, 2, 21),
	(1079, 2, 22),
	(1080, 2, 23),
	(1081, 2, 32),
	(1082, 2, 42),
	(1083, 2, 43),
	(1084, 2, 65),
	(1085, 2, 66),
	(1086, 2, 67),
	(1087, 2, 69),
	(1088, 2, 85),
	(1089, 2, 86),
	(1090, 2, 90),
	(1091, 2, 100),
	(1092, 2, 162),
	(1093, 2, 163),
	(1094, 2, 164),
	(1095, 2, 165),
	(1096, 2, 166),
	(1097, 2, 168),
	(1098, 2, 169),
	(1099, 2, 170),
	(1100, 2, 171),
	(1101, 2, 172),
	(1102, 2, 173),
	(1103, 2, 174),
	(1104, 2, 176),
	(1105, 2, 177),
	(1106, 2, 178),
	(1107, 2, 179),
	(1108, 2, 180),
	(1114, 3, 11),
	(1115, 3, 21),
	(1116, 3, 32),
	(1117, 3, 42),
	(1118, 3, 43),
	(1119, 3, 65),
	(1120, 3, 66),
	(1121, 3, 85),
	(1122, 3, 86),
	(1123, 3, 90),
	(1124, 3, 163),
	(1125, 3, 165);
INSERT INTO public.rol_permisos (id, rol_id, permiso_id) VALUES
	(1126, 3, 169),
	(1127, 3, 170),
	(1128, 3, 172),
	(1129, 3, 177),
	(1130, 3, 178),
	(1135, 1, 188),
	(1136, 5, 188),
	(1137, 1, 189),
	(1138, 5, 189),
	(1139, 1, 190),
	(1140, 5, 190),
	(1141, 1, 191),
	(1142, 5, 191),
	(1143, 1, 192),
	(1144, 5, 192),
	(1145, 1, 193),
	(1146, 5, 193),
	(1147, 1, 194),
	(1148, 5, 194),
	(1149, 1, 195),
	(1150, 5, 195),
	(1151, 1, 196),
	(1152, 5, 196),
	(1153, 1, 197),
	(1154, 5, 197),
	(1155, 2, 191),
	(1156, 2, 192),
	(1157, 2, 193),
	(1158, 2, 194),
	(1159, 2, 196),
	(1160, 2, 197),
	(1161, 3, 191),
	(1162, 3, 193),
	(1163, 3, 196),
	(1164, 1, 198),
	(1165, 5, 198),
	(1166, 1, 199),
	(1167, 5, 199),
	(1168, 1, 200),
	(1169, 5, 200),
	(1171, 2, 198),
	(1172, 3, 198),
	(1175, 2, 200),
	(1176, 3, 200),
	(1179, 2, 199),
	(1181, 1, 201),
	(1182, 5, 201),
	(1184, 2, 201),
	(1186, 1, 202),
	(1187, 5, 202),
	(1189, 2, 202),
	(1190, 3, 202),
	(1192, 1, 203),
	(1193, 5, 203),
	(1194, 1, 204),
	(1195, 5, 204),
	(1196, 1, 205),
	(1197, 5, 205),
	(1198, 1, 206),
	(1199, 5, 206),
	(1200, 1, 207),
	(1201, 5, 207),
	(1202, 1, 208),
	(1203, 5, 208),
	(1204, 1, 209),
	(1205, 5, 209),
	(1206, 2, 207),
	(1207, 2, 206),
	(1208, 2, 204),
	(1209, 2, 208),
	(1210, 2, 209),
	(1211, 2, 203),
	(1212, 3, 204),
	(1213, 3, 208),
	(1214, 3, 209),
	(1215, 3, 203),
	(1216, 1, 210),
	(1217, 2, 210),
	(1218, 3, 210),
	(1219, 5, 210);


--
-- Data for Name: roles; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.roles (id, nombre, descripcion, activo, sistema, lock_version) VALUES
	(2, 'Jefe de Unidad', 'Jefe de una unidad organizacional', true, true, 0),
	(3, 'Miembro', 'Miembro de una unidad organizacional', true, true, 0),
	(4, 'Invitado', 'Rol exclusivo para reuniones (no asignable a cuentas)', true, true, 0),
	(1, 'Superadmin', 'Administración global del sistema con acceso completo', true, true, 0),
	(10, 'Secretaria', 'Secretaría: gestión documental y correspondencia institucional.', true, true, 0),
	(5, 'Administrador', 'Administración operativa del sistema sin acceso a configuración global', false, true, 0),
	(8, 'Director', 'Dirección: control operativo institucional sin administración técnica.', true, true, 0),
	(9, 'Subdirector', 'Subdirección: funciones operativas dentro de sus recursos y ámbito autorizado.', true, true, 0);


--
-- Data for Name: security_policies; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.security_policies (id, nombre, valor, descripcion, categoria, created_at, updated_at, lock_version) VALUES
	(8, 'Longitud mínima de contraseña', '8', 'Número mínimo de caracteres para contraseñas', 'Contrasena', '2026-10-12 08:59:07.188858', '2026-10-15 07:35:43.322441', 4),
	(20, 'Tiempo de expiración JWT (min)', '60', 'Minutos antes de que expire el token JWT', 'JWT', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(9, 'Longitud máxima de contraseña', '128', 'Número máximo de caracteres para contraseñas', 'Contrasena', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(10, 'Requiere mayúsculas', 'true', 'Exigir al menos una letra mayúscula', 'Contrasena', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(11, 'Requiere minúsculas', 'true', 'Exigir al menos una letra minúscula', 'Contrasena', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(12, 'Requiere números', 'true', 'Exigir al menos un número', 'Contrasena', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(13, 'Requiere caracteres especiales', 'true', 'Exigir al menos un caracter especial (!@#$%^&*)', 'Contrasena', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(14, 'Historial de contraseñas', '5', 'Número de contraseñas anteriores que no se pueden reutilizar', 'Contrasena', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(15, 'Expiración de contraseña (días)', '90', 'Días de validez de una contraseña antes de expirar', 'Contrasena', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(16, 'Cambio obligatorio de contraseña', 'true', 'Forzar el cambio de contraseña al primer inicio de sesión', 'Contrasena', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(17, 'Autenticación de dos factores', 'false', 'Habilitar la autenticación 2FA para todos los usuarios', 'Autenticacion', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(18, 'Tiempo de expiración del token temporal 2FA (seg)', '300', 'Segundos de validez del token temporal para 2FA', 'Autenticacion', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(19, 'Requiere cambio de contraseña', 'false', 'Forzar el cambio de contraseña periódicamente', 'Autenticacion', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(21, 'Tiempo máximo de inactividad (min)', '30', 'Minutos de inactividad antes de cerrar sesión', 'JWT', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(22, 'Duración máxima de sesión (min)', '480', 'Duración máxima de una sesión en minutos', 'JWT', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(23, 'Renovación automática del token', 'true', 'Renovar automáticamente el token antes de expirar', 'JWT', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(24, 'Cierre automático por inactividad', 'true', 'Cerrar sesión automáticamente por inactividad', 'JWT', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(27, 'Política de bloqueo', 'TEMPORAL', 'Tipo de bloqueo: TEMPORAL, PERMANENTE o ASCENDENTE', 'Acceso', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(29, 'Bloqueo ascendente', 'false', 'Incrementar duracion del bloqueo con cada intento', 'Acceso', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(30, 'Restricción por IP', 'false', 'Habilitar la restricción de acceso por dirección IP', 'IP', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(31, 'Restricción por rango de IP', 'false', 'Habilitar la restricción por rango de direcciones IP', 'IP', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(32, 'Restricción por horario', 'false', 'Habilitar la restricción de acceso por horario', 'IP', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(33, 'Restricción por ubicación', 'false', 'Habilitar la restricción de acceso por ubicación geográfica', 'IP', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(34, 'Horario inicio acceso', '06:00', 'Hora de inicio del horario permitido de acceso', 'IP', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(35, 'Horario fin acceso', '22:00', 'Hora de fin del horario permitido de acceso', 'IP', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(36, 'Lista blanca de IP activa', 'false', 'Activar lista blanca de direcciones IP permitidas', 'IP', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(37, 'Lista negra de IP activa', 'false', 'Activar lista negra de direcciones IP bloqueadas', 'IP', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(40, 'Métodos HTTP permitidos', 'GET,POST,PUT,DELETE,PATCH,OPTIONS', 'Metodos HTTP permitidos separados por coma', 'CORS', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(41, 'Encabezados permitidos', '*', 'Encabezados HTTP permitidos separados por coma', 'CORS', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(46, 'Registro de eventos críticos', 'true', 'Registrar eventos críticos del sistema en la auditoría', 'Auditoria', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(47, 'Registro de accesos', 'true', 'Registrar intentos de inicio de sesion exitosos y fallidos', 'Auditoria', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(48, 'Registro de cambios de datos', 'true', 'Registrar modificaciones a datos sensibles', 'Auditoria', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(49, 'Retención de registros (días)', '365', 'Días de retención de registros de auditoría', 'Auditoria', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(25, 'Intentos fallidos máximos', '5', 'Número máximo de intentos fallidos antes de bloquear', 'Acceso', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(43, 'Tamaño máximo de archivos (MB)', '10', 'Tamaño máximo por archivo en megabytes', 'Carga', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(45, 'Límite de carga por petición (MB)', '50', 'Tamaño máximo total por petición multipart en megabytes', 'Carga', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(50, 'Políticas de auditoría', 'COMPLETA', 'Nivel de auditoría: COMPLETA, BÁSICA o NINGUNA', 'Auditoria', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(26, 'Tiempo de bloqueo (min)', '30', 'Duracion del bloqueo por intentos fallidos en minutos', 'Acceso', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(38, 'CORS habilitado', 'true', 'Activar/desactivar configuracion CORS', 'CORS', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(42, 'Credenciales permitidas', 'true', 'Permitir envio de credenciales en peticiones CORS', 'CORS', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(44, 'Tipos de archivos permitidos', 'pdf,doc,docx,xls,xlsx,ppt,pptx,jpg,jpeg,png,gif,zip,rar,txt,csv,webp', 'Extensiones de archivo permitidas separadas por coma', 'Carga', '2026-10-12 08:59:07.188858', '2026-10-12 08:59:07.188858', 0),
	(39, 'Orígenes permitidos', 'https://localhost:4200,https://192.168.41.9:4200,http://localhost:4200,http://192.168.41.9:4200,http://localhost,https://localhost,http://192.168.41.26:4200', 'Origenes permitidos separados por coma', 'CORS', '2026-10-12 08:59:07.188858', '2026-10-15 07:35:41.100628', 5);


--
-- Data for Name: solicitud_aprobaciones; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: solicitud_etapas; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: solicitud_historial; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: solicitud_tipos; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: solicitudes; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: tarea_adjuntos; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: tarea_checklist; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: tarea_participantes_usuario; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: tareas; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: tipos_documento; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: ubicaciones; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: unidades; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.unidades (id, nombre, descripcion, activo) VALUES
	(1, 'Unidad de Operaciones', 'Unidad encargada de las operaciones del sistema', true),
	(2, 'Unidad de Comunicación Digital', 'Unidad encargada de la comunicación digital', true),
	(3, 'Unidad de Análisis y Estadística', 'Unidad encargada del análisis de datos y estadísticas', true),
	(4, 'Unidad de Tecnología y Estructura', 'Unidad encargada de la tecnología y estructura organizacional', true),
	(5, 'Unidad de Comunicación Estratégica', 'Unidad encargada de la comunicación estratégica', true),
	(6, 'Unidad de Gestión Administrativa', 'Unidad encargada de la gestión administrativa', true),
	(7, 'Asesoría Legal', 'Unidad encargada de la asesoría legal', true);



--
-- Data for Name: user_preferences; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: usuario_roles; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.usuario_roles (id, usuario_id, rol_id) VALUES
	(1, 1, 1);


--
-- Data for Name: usuario_unidad; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: usuarios; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.usuarios (id, nombre, apellido, correo, password, activo, bloqueado, creado_en, ultimo_acceso, intentos_fallidos, bloqueado_hasta, segundo_nombre, documento_identidad, tipo_documento, correo_alterno, telefono, celular, cargo, departamento, direccion, unidad_administrativa, username, fecha_expiracion, cambio_contrasena_requerido, fotografia_ruta, codigo_interno, observaciones, actualizado_en, institucion, token_version, contrasena_temporal_expira_en) VALUES
	(1, 'Administrador', 'SIGAA', 'admin@sigaa.com', '$2a$10$35HffJseO6rogipo832cuu9P3XrW44AUm7UReBNdbO6JRL4jaSbqe', true, false, '2026-10-12 08:59:05.20291', '2026-10-28 08:14:37.008851', 0, NULL, NULL, NULL, 'CEDULA', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'usuario-1', NULL, false, NULL, NULL, NULL, '2026-10-28 08:14:37.021856', NULL, 0, NULL);


--
-- Data for Name: usuarios_2fa; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Name: acta_acuerdos_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.acta_acuerdos_id_seq', 1, false);


--
-- Name: acta_adjuntos_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.acta_adjuntos_id_seq', 1, false);


--
-- Name: acta_firmas_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.acta_firmas_id_seq', 1, false);


--
-- Name: acta_participantes_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.acta_participantes_id_seq', 1, false);


--
-- Name: actas_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.actas_id_seq', 1, false);


--
-- Name: admin_password_reset_requests_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.admin_password_reset_requests_id_seq', 1, false);


--
-- Name: allowed_http_headers_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.allowed_http_headers_id_seq', 1, false);


--
-- Name: allowed_http_methods_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.allowed_http_methods_id_seq', 1, false);


--
-- Name: allowed_origins_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.allowed_origins_id_seq', 1, false);


--
-- Name: asistencia_invitados_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.asistencia_invitados_id_seq', 1, false);


--
-- Name: asistencia_registros_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.asistencia_registros_id_seq', 1, false);


--
-- Name: asistencia_sesiones_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.asistencia_sesiones_id_seq', 1, false);


--
-- Name: audit_logs_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.audit_logs_id_seq', 1, false);


--
-- Name: backups_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.backups_id_seq', 1, false);


--
-- Name: carpeta_documentos_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.carpeta_documentos_id_seq', 1, false);


--
-- Name: carpeta_usuarios_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.carpeta_usuarios_id_seq', 1, false);


--
-- Name: carpetas_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.carpetas_id_seq', 1, false);


--
-- Name: documento_solicitudes_aprobacion_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.documento_solicitudes_aprobacion_id_seq', 1, false);


--
-- Name: documento_usuarios_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.documento_usuarios_id_seq', 1, false);


--
-- Name: documento_versiones_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.documento_versiones_id_seq', 1, false);


--
-- Name: documentos_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.documentos_id_seq', 1, false);


--
-- Name: grupo_mensajes_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.grupo_mensajes_id_seq', 1, false);


--
-- Name: grupo_permisos_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.grupo_permisos_id_seq', 1, false);


--
-- Name: grupo_usuarios_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.grupo_usuarios_id_seq', 1, false);


--
-- Name: grupos_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.grupos_id_seq', 1, false);


--
-- Name: horarios_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.horarios_id_seq', 1, false);


--
-- Name: inventario_activos_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.inventario_activos_id_seq', 1, false);


--
-- Name: ip_restrictions_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.ip_restrictions_id_seq', 1, false);


--
-- Name: memo_historial_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.memo_historial_id_seq', 1, false);


--
-- Name: memo_numeros_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.memo_numeros_id_seq', 1, false);


--
-- Name: memos_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.memos_id_seq', 1, false);


--
-- Name: modulos_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.modulos_id_seq', 16, true);


--
-- Name: nota_historial_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.nota_historial_id_seq', 1, false);


--
-- Name: nota_numeros_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.nota_numeros_id_seq', 1, false);


--
-- Name: nota_pdfs_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.nota_pdfs_id_seq', 1, false);


--
-- Name: nota_relaciones_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.nota_relaciones_id_seq', 1, false);


--
-- Name: notas_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.notas_id_seq', 1, false);


--
-- Name: notificaciones_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.notificaciones_id_seq', 1, false);


--
-- Name: notification_channels_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.notification_channels_id_seq', 1, false);


--
-- Name: notification_events_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.notification_events_id_seq', 1, false);


--
-- Name: notification_global_settings_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.notification_global_settings_id_seq', 1, false);


--
-- Name: notification_history_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.notification_history_id_seq', 1, false);


--
-- Name: notification_recipients_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.notification_recipients_id_seq', 1, false);


--
-- Name: notification_settings_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.notification_settings_id_seq', 1, false);


--
-- Name: notification_templates_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.notification_templates_id_seq', 1, false);


--
-- Name: org_units_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.org_units_id_seq', 17, true);


--
-- Name: organization_departments_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.organization_departments_id_seq', 1, false);


--
-- Name: organization_settings_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.organization_settings_id_seq', 5, true);


--
-- Name: organizations_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.organizations_id_seq', 17, true);


--
-- Name: parameter_history_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.parameter_history_id_seq', 1, false);


--
-- Name: parametros_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.parametros_id_seq', 1, false);


--
-- Name: password_history_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.password_history_id_seq', 1, false);


--
-- Name: password_reset_requests_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.password_reset_requests_id_seq', 1, false);


--
-- Name: permisos_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.permisos_id_seq', 212, true);


--
-- Name: reporte_programaciones_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.reporte_programaciones_id_seq', 1, false);


--
-- Name: reportes_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.reportes_id_seq', 1, false);


--
-- Name: reuniones_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.reuniones_id_seq', 1, false);


--
-- Name: rol_permisos_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.rol_permisos_id_seq', 1653, true);


--
-- Name: roles_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.roles_id_seq', 10, true);


--
-- Name: security_policies_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.security_policies_id_seq', 50, true);


--
-- Name: solicitud_aprobaciones_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.solicitud_aprobaciones_id_seq', 1, false);


--
-- Name: solicitud_etapas_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.solicitud_etapas_id_seq', 1, false);


--
-- Name: solicitud_historial_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.solicitud_historial_id_seq', 1, false);


--
-- Name: solicitud_tipos_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.solicitud_tipos_id_seq', 1, false);


--
-- Name: solicitudes_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.solicitudes_id_seq', 1, false);


--
-- Name: tarea_adjuntos_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.tarea_adjuntos_id_seq', 1, false);


--
-- Name: tarea_checklist_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.tarea_checklist_id_seq', 1, false);


--
-- Name: tareas_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.tareas_id_seq', 1, false);


--
-- Name: tipos_documento_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.tipos_documento_id_seq', 1, false);


--
-- Name: ubicaciones_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.ubicaciones_id_seq', 1, false);


--
-- Name: unidades_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.unidades_id_seq', 8, true);


--
-- Name: user_preferences_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.user_preferences_id_seq', 1, false);


--
-- Name: usuario_roles_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.usuario_roles_id_seq', 1, true);


--
-- Name: usuario_unidad_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.usuario_unidad_id_seq', 1, false);


--
-- Name: usuarios_2fa_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.usuarios_2fa_id_seq', 1, false);


--
-- Name: usuarios_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.usuarios_id_seq', 1, true);


--
-- Name: acta_acuerdos acta_acuerdos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acta_acuerdos
    ADD CONSTRAINT acta_acuerdos_pkey PRIMARY KEY (id);


--
-- Name: acta_adjuntos acta_adjuntos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acta_adjuntos
    ADD CONSTRAINT acta_adjuntos_pkey PRIMARY KEY (id);


--
-- Name: acta_firmas acta_firmas_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acta_firmas
    ADD CONSTRAINT acta_firmas_pkey PRIMARY KEY (id);


--
-- Name: acta_participantes acta_participantes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acta_participantes
    ADD CONSTRAINT acta_participantes_pkey PRIMARY KEY (id);


--
-- Name: actas actas_codigo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.actas
    ADD CONSTRAINT actas_codigo_key UNIQUE (codigo);


--
-- Name: actas actas_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.actas
    ADD CONSTRAINT actas_pkey PRIMARY KEY (id);


--
-- Name: admin_password_reset_requests admin_password_reset_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_password_reset_requests
    ADD CONSTRAINT admin_password_reset_requests_pkey PRIMARY KEY (id);


--
-- Name: allowed_http_headers allowed_http_headers_header_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.allowed_http_headers
    ADD CONSTRAINT allowed_http_headers_header_key UNIQUE (header);


--
-- Name: allowed_http_headers allowed_http_headers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.allowed_http_headers
    ADD CONSTRAINT allowed_http_headers_pkey PRIMARY KEY (id);


--
-- Name: allowed_http_methods allowed_http_methods_metodo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.allowed_http_methods
    ADD CONSTRAINT allowed_http_methods_metodo_key UNIQUE (metodo);


--
-- Name: allowed_http_methods allowed_http_methods_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.allowed_http_methods
    ADD CONSTRAINT allowed_http_methods_pkey PRIMARY KEY (id);


--
-- Name: allowed_origins allowed_origins_origen_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.allowed_origins
    ADD CONSTRAINT allowed_origins_origen_key UNIQUE (origen);


--
-- Name: allowed_origins allowed_origins_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.allowed_origins
    ADD CONSTRAINT allowed_origins_pkey PRIMARY KEY (id);


--
-- Name: asistencia_invitados asistencia_invitados_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asistencia_invitados
    ADD CONSTRAINT asistencia_invitados_pkey PRIMARY KEY (id);


--
-- Name: asistencia_registros asistencia_registros_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asistencia_registros
    ADD CONSTRAINT asistencia_registros_pkey PRIMARY KEY (id);


--
-- Name: asistencia_sesiones asistencia_sesiones_codigo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asistencia_sesiones
    ADD CONSTRAINT asistencia_sesiones_codigo_key UNIQUE (codigo);


--
-- Name: asistencia_sesiones asistencia_sesiones_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asistencia_sesiones
    ADD CONSTRAINT asistencia_sesiones_pkey PRIMARY KEY (id);


--
-- Name: audit_logs audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id);


--
-- Name: backups backups_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.backups
    ADD CONSTRAINT backups_pkey PRIMARY KEY (id);


--
-- Name: carpeta_documentos carpeta_documentos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carpeta_documentos
    ADD CONSTRAINT carpeta_documentos_pkey PRIMARY KEY (id);


--
-- Name: carpeta_usuarios carpeta_usuarios_carpeta_id_usuario_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carpeta_usuarios
    ADD CONSTRAINT carpeta_usuarios_carpeta_id_usuario_id_key UNIQUE (carpeta_id, usuario_id);


--
-- Name: carpeta_usuarios carpeta_usuarios_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carpeta_usuarios
    ADD CONSTRAINT carpeta_usuarios_pkey PRIMARY KEY (id);


--
-- Name: carpetas carpetas_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carpetas
    ADD CONSTRAINT carpetas_pkey PRIMARY KEY (id);


--
-- Name: documento_solicitudes_aprobacion documento_solicitudes_aprobacion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_solicitudes_aprobacion
    ADD CONSTRAINT documento_solicitudes_aprobacion_pkey PRIMARY KEY (id);


--
-- Name: documento_usuarios documento_usuarios_documento_id_usuario_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_usuarios
    ADD CONSTRAINT documento_usuarios_documento_id_usuario_id_key UNIQUE (documento_id, usuario_id);


--
-- Name: documento_usuarios documento_usuarios_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_usuarios
    ADD CONSTRAINT documento_usuarios_pkey PRIMARY KEY (id);


--
-- Name: documento_versiones documento_versiones_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_versiones
    ADD CONSTRAINT documento_versiones_pkey PRIMARY KEY (id);


--
-- Name: documentos documentos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documentos
    ADD CONSTRAINT documentos_pkey PRIMARY KEY (id);


--
-- Name: grupo_mensajes grupo_mensajes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grupo_mensajes
    ADD CONSTRAINT grupo_mensajes_pkey PRIMARY KEY (id);


--
-- Name: grupo_permisos grupo_permisos_grupo_id_permiso_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grupo_permisos
    ADD CONSTRAINT grupo_permisos_grupo_id_permiso_id_key UNIQUE (grupo_id, permiso_id);


--
-- Name: grupo_permisos grupo_permisos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grupo_permisos
    ADD CONSTRAINT grupo_permisos_pkey PRIMARY KEY (id);


--
-- Name: grupo_usuarios grupo_usuarios_grupo_id_usuario_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grupo_usuarios
    ADD CONSTRAINT grupo_usuarios_grupo_id_usuario_id_key UNIQUE (grupo_id, usuario_id);


--
-- Name: grupo_usuarios grupo_usuarios_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grupo_usuarios
    ADD CONSTRAINT grupo_usuarios_pkey PRIMARY KEY (id);


--
-- Name: grupos grupos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grupos
    ADD CONSTRAINT grupos_pkey PRIMARY KEY (id);


--
-- Name: grupos grupos_unidad_id_nombre_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grupos
    ADD CONSTRAINT grupos_unidad_id_nombre_key UNIQUE (unidad_id, nombre);


--
-- Name: horarios horarios_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.horarios
    ADD CONSTRAINT horarios_pkey PRIMARY KEY (id);


--
-- Name: horarios horarios_usuario_id_dia_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.horarios
    ADD CONSTRAINT horarios_usuario_id_dia_key UNIQUE (usuario_id, dia);


--
-- Name: inventario_activos inventario_activos_codigo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventario_activos
    ADD CONSTRAINT inventario_activos_codigo_key UNIQUE (codigo);


--
-- Name: inventario_activos inventario_activos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventario_activos
    ADD CONSTRAINT inventario_activos_pkey PRIMARY KEY (id);


--
-- Name: ip_restrictions ip_restrictions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ip_restrictions
    ADD CONSTRAINT ip_restrictions_pkey PRIMARY KEY (id);


--
-- Name: ip_restrictions ip_restrictions_tipo_direccion_ip_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ip_restrictions
    ADD CONSTRAINT ip_restrictions_tipo_direccion_ip_key UNIQUE (tipo, direccion_ip);


--
-- Name: memo_historial memo_historial_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.memo_historial
    ADD CONSTRAINT memo_historial_pkey PRIMARY KEY (id);


--
-- Name: memo_numeros memo_numeros_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.memo_numeros
    ADD CONSTRAINT memo_numeros_pkey PRIMARY KEY (id);


--
-- Name: memos memos_numero_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.memos
    ADD CONSTRAINT memos_numero_id_key UNIQUE (numero_id);


--
-- Name: memos memos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.memos
    ADD CONSTRAINT memos_pkey PRIMARY KEY (id);


--
-- Name: modulos modulos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.modulos
    ADD CONSTRAINT modulos_pkey PRIMARY KEY (id);


--
-- Name: nota_historial nota_historial_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.nota_historial
    ADD CONSTRAINT nota_historial_pkey PRIMARY KEY (id);


--
-- Name: nota_numeros nota_numeros_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.nota_numeros
    ADD CONSTRAINT nota_numeros_pkey PRIMARY KEY (id);


--
-- Name: nota_pdfs nota_pdfs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.nota_pdfs
    ADD CONSTRAINT nota_pdfs_pkey PRIMARY KEY (id);


--
-- Name: nota_relaciones nota_relaciones_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.nota_relaciones
    ADD CONSTRAINT nota_relaciones_pkey PRIMARY KEY (id);


--
-- Name: notas notas_numero_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notas
    ADD CONSTRAINT notas_numero_id_key UNIQUE (numero_id);


--
-- Name: notas notas_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notas
    ADD CONSTRAINT notas_pkey PRIMARY KEY (id);


--
-- Name: notificaciones notificaciones_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notificaciones
    ADD CONSTRAINT notificaciones_pkey PRIMARY KEY (id);


--
-- Name: notification_channels notification_channels_nombre_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_channels
    ADD CONSTRAINT notification_channels_nombre_key UNIQUE (nombre);


--
-- Name: notification_channels notification_channels_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_channels
    ADD CONSTRAINT notification_channels_pkey PRIMARY KEY (id);


--
-- Name: notification_events notification_events_codigo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_events
    ADD CONSTRAINT notification_events_codigo_key UNIQUE (codigo);


--
-- Name: notification_events notification_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_events
    ADD CONSTRAINT notification_events_pkey PRIMARY KEY (id);


--
-- Name: notification_global_settings notification_global_settings_clave_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_global_settings
    ADD CONSTRAINT notification_global_settings_clave_key UNIQUE (clave);


--
-- Name: notification_global_settings notification_global_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_global_settings
    ADD CONSTRAINT notification_global_settings_pkey PRIMARY KEY (id);


--
-- Name: notification_history notification_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_history
    ADD CONSTRAINT notification_history_pkey PRIMARY KEY (id);


--
-- Name: notification_recipients notification_recipients_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_recipients
    ADD CONSTRAINT notification_recipients_pkey PRIMARY KEY (id);


--
-- Name: notification_settings notification_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_settings
    ADD CONSTRAINT notification_settings_pkey PRIMARY KEY (id);


--
-- Name: notification_templates notification_templates_nombre_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_templates
    ADD CONSTRAINT notification_templates_nombre_key UNIQUE (nombre);


--
-- Name: notification_templates notification_templates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_templates
    ADD CONSTRAINT notification_templates_pkey PRIMARY KEY (id);


--
-- Name: org_units org_units_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.org_units
    ADD CONSTRAINT org_units_pkey PRIMARY KEY (id);


--
-- Name: organization_departments organization_departments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization_departments
    ADD CONSTRAINT organization_departments_pkey PRIMARY KEY (id);


--
-- Name: organization_settings organization_settings_organization_id_clave_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization_settings
    ADD CONSTRAINT organization_settings_organization_id_clave_key UNIQUE (organization_id, clave);


--
-- Name: organization_settings organization_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization_settings
    ADD CONSTRAINT organization_settings_pkey PRIMARY KEY (id);


--
-- Name: organizations organizations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organizations
    ADD CONSTRAINT organizations_pkey PRIMARY KEY (id);


--
-- Name: parameter_history parameter_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parameter_history
    ADD CONSTRAINT parameter_history_pkey PRIMARY KEY (id);


--
-- Name: parametros parametros_clave_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parametros
    ADD CONSTRAINT parametros_clave_key UNIQUE (clave);


--
-- Name: parametros parametros_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parametros
    ADD CONSTRAINT parametros_pkey PRIMARY KEY (id);


--
-- Name: password_history password_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_history
    ADD CONSTRAINT password_history_pkey PRIMARY KEY (id);


--
-- Name: password_reset_requests password_reset_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_requests
    ADD CONSTRAINT password_reset_requests_pkey PRIMARY KEY (id);


--
-- Name: permisos permisos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permisos
    ADD CONSTRAINT permisos_pkey PRIMARY KEY (id);


--
-- Name: reporte_programaciones reporte_programaciones_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reporte_programaciones
    ADD CONSTRAINT reporte_programaciones_pkey PRIMARY KEY (id);


--
-- Name: reportes reportes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reportes
    ADD CONSTRAINT reportes_pkey PRIMARY KEY (id);


--
-- Name: reunion_participantes_usuario reunion_participantes_usuario_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reunion_participantes_usuario
    ADD CONSTRAINT reunion_participantes_usuario_pkey PRIMARY KEY (reunion_id, usuario_id);


--
-- Name: reunion_unidades reunion_unidades_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reunion_unidades
    ADD CONSTRAINT reunion_unidades_pkey PRIMARY KEY (reunion_id, unidad_id);


--
-- Name: reuniones reuniones_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reuniones
    ADD CONSTRAINT reuniones_pkey PRIMARY KEY (id);


--
-- Name: rol_permisos rol_permisos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rol_permisos
    ADD CONSTRAINT rol_permisos_pkey PRIMARY KEY (id);


--
-- Name: rol_permisos rol_permisos_rol_id_permiso_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rol_permisos
    ADD CONSTRAINT rol_permisos_rol_id_permiso_id_key UNIQUE (rol_id, permiso_id);


--
-- Name: roles roles_nombre_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_nombre_key UNIQUE (nombre);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);


--
-- Name: security_policies security_policies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.security_policies
    ADD CONSTRAINT security_policies_pkey PRIMARY KEY (id);


--
-- Name: solicitud_aprobaciones solicitud_aprobaciones_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_aprobaciones
    ADD CONSTRAINT solicitud_aprobaciones_pkey PRIMARY KEY (id);


--
-- Name: solicitud_etapas solicitud_etapas_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_etapas
    ADD CONSTRAINT solicitud_etapas_pkey PRIMARY KEY (id);


--
-- Name: solicitud_etapas solicitud_etapas_tipo_id_orden_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_etapas
    ADD CONSTRAINT solicitud_etapas_tipo_id_orden_key UNIQUE (tipo_id, orden);


--
-- Name: solicitud_historial solicitud_historial_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_historial
    ADD CONSTRAINT solicitud_historial_pkey PRIMARY KEY (id);


--
-- Name: solicitud_tipos solicitud_tipos_codigo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_tipos
    ADD CONSTRAINT solicitud_tipos_codigo_key UNIQUE (codigo);


--
-- Name: solicitud_tipos solicitud_tipos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_tipos
    ADD CONSTRAINT solicitud_tipos_pkey PRIMARY KEY (id);


--
-- Name: solicitudes solicitudes_codigo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitudes
    ADD CONSTRAINT solicitudes_codigo_key UNIQUE (codigo);


--
-- Name: solicitudes solicitudes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitudes
    ADD CONSTRAINT solicitudes_pkey PRIMARY KEY (id);


--
-- Name: tarea_adjuntos tarea_adjuntos_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tarea_adjuntos
    ADD CONSTRAINT tarea_adjuntos_pkey PRIMARY KEY (id);


--
-- Name: tarea_checklist tarea_checklist_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tarea_checklist
    ADD CONSTRAINT tarea_checklist_pkey PRIMARY KEY (id);


--
-- Name: tarea_participantes_usuario tarea_participantes_usuario_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tarea_participantes_usuario
    ADD CONSTRAINT tarea_participantes_usuario_pkey PRIMARY KEY (tarea_id, usuario_id);


--
-- Name: tareas tareas_codigo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tareas
    ADD CONSTRAINT tareas_codigo_key UNIQUE (codigo);


--
-- Name: tareas tareas_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tareas
    ADD CONSTRAINT tareas_pkey PRIMARY KEY (id);


--
-- Name: tipos_documento tipos_documento_codigo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tipos_documento
    ADD CONSTRAINT tipos_documento_codigo_key UNIQUE (codigo);


--
-- Name: tipos_documento tipos_documento_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tipos_documento
    ADD CONSTRAINT tipos_documento_pkey PRIMARY KEY (id);


--
-- Name: ubicaciones ubicaciones_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ubicaciones
    ADD CONSTRAINT ubicaciones_pkey PRIMARY KEY (id);


--
-- Name: reuniones uk_reuniones_codigo; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reuniones
    ADD CONSTRAINT uk_reuniones_codigo UNIQUE (codigo);


--
-- Name: user_preferences uk_user_preferences_usuario; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_preferences
    ADD CONSTRAINT uk_user_preferences_usuario UNIQUE (usuario_id);


--
-- Name: unidades unidades_nombre_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.unidades
    ADD CONSTRAINT unidades_nombre_key UNIQUE (nombre);


--
-- Name: unidades unidades_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.unidades
    ADD CONSTRAINT unidades_pkey PRIMARY KEY (id);


--
-- Name: modulos uq_modulos_orden; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.modulos
    ADD CONSTRAINT uq_modulos_orden UNIQUE (orden) DEFERRABLE INITIALLY DEFERRED;


--
-- Name: nota_pdfs uq_nota_pdfs_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.nota_pdfs
    ADD CONSTRAINT uq_nota_pdfs_version UNIQUE (nota_id, numero_version);


--
-- Name: nota_relaciones uq_nota_relacion_direccion; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.nota_relaciones
    ADD CONSTRAINT uq_nota_relacion_direccion UNIQUE (nota_origen_id, nota_destino_id, tipo);


--
-- Name: notification_recipients uq_recipient_evento_rol; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_recipients
    ADD CONSTRAINT uq_recipient_evento_rol UNIQUE (evento_id, rol_id);


--
-- Name: notification_settings uq_setting_evento_canal; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_settings
    ADD CONSTRAINT uq_setting_evento_canal UNIQUE (evento_id, canal);


--
-- Name: user_preferences user_preferences_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_preferences
    ADD CONSTRAINT user_preferences_pkey PRIMARY KEY (id);


--
-- Name: usuario_roles usuario_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_roles
    ADD CONSTRAINT usuario_roles_pkey PRIMARY KEY (id);


--
-- Name: usuario_roles usuario_roles_usuario_id_rol_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_roles
    ADD CONSTRAINT usuario_roles_usuario_id_rol_id_key UNIQUE (usuario_id, rol_id);


--
-- Name: usuario_unidad usuario_unidad_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_unidad
    ADD CONSTRAINT usuario_unidad_pkey PRIMARY KEY (id);


--
-- Name: usuario_unidad usuario_unidad_usuario_id_unidad_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_unidad
    ADD CONSTRAINT usuario_unidad_usuario_id_unidad_id_key UNIQUE (usuario_id, unidad_id);


--
-- Name: usuarios_2fa usuarios_2fa_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuarios_2fa
    ADD CONSTRAINT usuarios_2fa_pkey PRIMARY KEY (id);


--
-- Name: usuarios_2fa usuarios_2fa_usuario_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuarios_2fa
    ADD CONSTRAINT usuarios_2fa_usuario_id_key UNIQUE (usuario_id);


--
-- Name: usuarios usuarios_correo_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuarios
    ADD CONSTRAINT usuarios_correo_key UNIQUE (correo);


--
-- Name: usuarios usuarios_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuarios
    ADD CONSTRAINT usuarios_pkey PRIMARY KEY (id);


--
-- Name: idx_acta_sesion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_acta_sesion ON public.actas USING btree (sesion_id);


--
-- Name: idx_asistencia_sesiones_archivado_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_asistencia_sesiones_archivado_fecha ON public.asistencia_sesiones USING btree (archivado, fecha DESC);


--
-- Name: idx_asistencia_usuario_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_asistencia_usuario_fecha ON public.asistencia_registros USING btree (usuario_id, fecha);


--
-- Name: idx_audit_logs_entidad; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_logs_entidad ON public.audit_logs USING btree (entidad, entidad_id);


--
-- Name: idx_backups_estado_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_backups_estado_fecha ON public.backups USING btree (estado, fecha DESC);


--
-- Name: idx_backups_verificacion_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_backups_verificacion_fecha ON public.backups USING btree (verificacion_estado, verificado_at DESC);


--
-- Name: idx_carpeta_usuarios_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_carpeta_usuarios_usuario ON public.carpeta_usuarios USING btree (usuario_id);


--
-- Name: idx_documento_usuarios_expira; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_documento_usuarios_expira ON public.documento_usuarios USING btree (expira_en);


--
-- Name: idx_documento_usuarios_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_documento_usuarios_usuario ON public.documento_usuarios USING btree (usuario_id);


--
-- Name: idx_documentos_clasificacion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_documentos_clasificacion ON public.documentos USING btree (clasificacion);


--
-- Name: idx_documentos_propietario_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_documentos_propietario_id ON public.documentos USING btree (propietario_id);


--
-- Name: idx_grupo_mensajes_grupo_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_grupo_mensajes_grupo_fecha ON public.grupo_mensajes USING btree (grupo_id, creado_en DESC);


--
-- Name: idx_grupo_usuarios_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_grupo_usuarios_usuario ON public.grupo_usuarios USING btree (usuario_id);


--
-- Name: idx_grupos_unidad; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_grupos_unidad ON public.grupos USING btree (unidad_id);


--
-- Name: idx_history_canal; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_history_canal ON public.notification_history USING btree (canal);


--
-- Name: idx_history_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_history_created ON public.notification_history USING btree (created_at);


--
-- Name: idx_history_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_history_estado ON public.notification_history USING btree (estado);


--
-- Name: idx_history_evento; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_history_evento ON public.notification_history USING btree (evento_codigo);


--
-- Name: idx_inventario_asignado_unidad; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inventario_asignado_unidad ON public.inventario_activos USING btree (asignado_unidad_id);


--
-- Name: idx_inventario_asignado_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inventario_asignado_usuario ON public.inventario_activos USING btree (asignado_usuario_id);


--
-- Name: idx_ip_restrictions_activo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ip_restrictions_activo ON public.ip_restrictions USING btree (activo);


--
-- Name: idx_ip_restrictions_tipo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_ip_restrictions_tipo ON public.ip_restrictions USING btree (tipo);


--
-- Name: idx_memo_historial_memo_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_memo_historial_memo_fecha ON public.memo_historial USING btree (memo_id, created_at);


--
-- Name: idx_memo_numeros_estado_numero; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_memo_numeros_estado_numero ON public.memo_numeros USING btree (estado, numero);


--
-- Name: idx_memo_numeros_reserva; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_memo_numeros_reserva ON public.memo_numeros USING btree (reservado_hasta) WHERE ((estado)::text = 'RESERVADO'::text);


--
-- Name: idx_memos_estado_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_memos_estado_created_at ON public.memos USING btree (estado, created_at DESC);


--
-- Name: idx_memos_seguimiento; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_memos_seguimiento ON public.memos USING btree (seguimiento_usuario_id, estado);


--
-- Name: idx_modulos_categoria; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_modulos_categoria ON public.modulos USING btree (categoria);


--
-- Name: idx_modulos_codigo; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_modulos_codigo ON public.modulos USING btree (codigo) WHERE (codigo IS NOT NULL);


--
-- Name: idx_modulos_orden; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_modulos_orden ON public.modulos USING btree (orden);


--
-- Name: idx_modulos_padre; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_modulos_padre ON public.modulos USING btree (padre_id);


--
-- Name: idx_nota_historial_nota_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_nota_historial_nota_fecha ON public.nota_historial USING btree (nota_id, created_at);


--
-- Name: idx_nota_numeros_estado_numero; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_nota_numeros_estado_numero ON public.nota_numeros USING btree (estado, numero);


--
-- Name: idx_nota_numeros_reserva_vencimiento; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_nota_numeros_reserva_vencimiento ON public.nota_numeros USING btree (reservado_hasta) WHERE ((estado)::text = 'RESERVADO'::text);


--
-- Name: idx_nota_pdfs_eliminados; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_nota_pdfs_eliminados ON public.nota_pdfs USING btree (nota_id, eliminado_at) WHERE (eliminado_at IS NOT NULL);


--
-- Name: idx_nota_pdfs_nota_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_nota_pdfs_nota_fecha ON public.nota_pdfs USING btree (nota_id, created_at DESC);


--
-- Name: idx_nota_relaciones_destino; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_nota_relaciones_destino ON public.nota_relaciones USING btree (nota_destino_id);


--
-- Name: idx_nota_relaciones_origen; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_nota_relaciones_origen ON public.nota_relaciones USING btree (nota_origen_id);


--
-- Name: idx_notas_creado_por; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notas_creado_por ON public.notas USING btree (creado_por_id, created_at DESC);


--
-- Name: idx_notas_departamento; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notas_departamento ON public.notas USING btree (departamento_id);


--
-- Name: idx_notas_estado_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notas_estado_created_at ON public.notas USING btree (estado, created_at DESC);


--
-- Name: idx_notas_fecha_oficial; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notas_fecha_oficial ON public.notas USING btree (fecha_oficial DESC);


--
-- Name: idx_notas_institucion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notas_institucion ON public.notas USING btree (institucion_id);


--
-- Name: idx_notas_seguimiento; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notas_seguimiento ON public.notas USING btree (seguimiento_usuario_id, estado);


--
-- Name: idx_notas_sensitiva; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notas_sensitiva ON public.notas USING btree (sensitiva) WHERE (sensitiva = true);


--
-- Name: idx_notificaciones_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notificaciones_usuario ON public.notificaciones USING btree (usuario_id);


--
-- Name: idx_notificaciones_usuario_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notificaciones_usuario_created_at ON public.notificaciones USING btree (usuario_id, created_at DESC);


--
-- Name: idx_notificaciones_usuario_leida; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_notificaciones_usuario_leida ON public.notificaciones USING btree (usuario_id, leida);


--
-- Name: idx_org_units_activo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_org_units_activo ON public.org_units USING btree (activo);


--
-- Name: idx_org_units_nivel; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_org_units_nivel ON public.org_units USING btree (nivel);


--
-- Name: idx_org_units_orden; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_org_units_orden ON public.org_units USING btree (orden);


--
-- Name: idx_org_units_padre; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_org_units_padre ON public.org_units USING btree (padre_id);


--
-- Name: idx_org_units_tipo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_org_units_tipo ON public.org_units USING btree (tipo);


--
-- Name: idx_organization_settings_org; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_organization_settings_org ON public.organization_settings USING btree (organization_id);


--
-- Name: idx_parameter_history_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_parameter_history_fecha ON public.parameter_history USING btree (fecha DESC);


--
-- Name: idx_parameter_history_parametro; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_parameter_history_parametro ON public.parameter_history USING btree (parametro_id);


--
-- Name: idx_parametros_categoria; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_parametros_categoria ON public.parametros USING btree (categoria);


--
-- Name: idx_parametros_codigo; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_parametros_codigo ON public.parametros USING btree (codigo) WHERE (codigo IS NOT NULL);


--
-- Name: idx_parametros_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_parametros_estado ON public.parametros USING btree (estado);


--
-- Name: idx_parametros_grupo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_parametros_grupo ON public.parametros USING btree (grupo);


--
-- Name: idx_parametros_modulo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_parametros_modulo ON public.parametros USING btree (modulo_id);


--
-- Name: idx_parametros_tipo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_parametros_tipo ON public.parametros USING btree (tipo);


--
-- Name: idx_password_history_usuario_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_password_history_usuario_created ON public.password_history USING btree (usuario_id, created_at DESC);


--
-- Name: idx_password_reset_requests_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_password_reset_requests_active ON public.password_reset_requests USING btree (user_id, used, blocked, expires_at);


--
-- Name: idx_password_reset_requests_expires_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_password_reset_requests_expires_at ON public.password_reset_requests USING btree (expires_at);


--
-- Name: idx_password_reset_requests_ip_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_password_reset_requests_ip_created_at ON public.password_reset_requests USING btree (request_ip, created_at);


--
-- Name: idx_password_reset_requests_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_password_reset_requests_user_id ON public.password_reset_requests USING btree (user_id);


--
-- Name: idx_permisos_accion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_permisos_accion ON public.permisos USING btree (accion);


--
-- Name: idx_permisos_activo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_permisos_activo ON public.permisos USING btree (activo);


--
-- Name: idx_permisos_modulo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_permisos_modulo ON public.permisos USING btree (modulo);


--
-- Name: idx_permisos_modulo_accion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_permisos_modulo_accion ON public.permisos USING btree (modulo, accion);


--
-- Name: idx_permisos_modulo_accion_recurso; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_permisos_modulo_accion_recurso ON public.permisos USING btree (modulo, accion, recurso);


--
-- Name: idx_permisos_recurso; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_permisos_recurso ON public.permisos USING btree (recurso);


--
-- Name: idx_recipients_evento; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_recipients_evento ON public.notification_recipients USING btree (evento_id);


--
-- Name: idx_recipients_rol; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_recipients_rol ON public.notification_recipients USING btree (rol_id);


--
-- Name: idx_registro_sesion; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_registro_sesion ON public.asistencia_registros USING btree (sesion_id);


--
-- Name: idx_reporte_programaciones_pendientes; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reporte_programaciones_pendientes ON public.reporte_programaciones USING btree (activo, proxima_ejecucion);


--
-- Name: idx_reunion_participante_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reunion_participante_usuario ON public.reunion_participantes_usuario USING btree (usuario_id, reunion_id);


--
-- Name: idx_reuniones_organizador_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_reuniones_organizador_usuario ON public.reuniones USING btree (organizador_usuario_id);


--
-- Name: idx_rol_permisos_permiso_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rol_permisos_permiso_id ON public.rol_permisos USING btree (permiso_id);


--
-- Name: idx_rol_permisos_rol_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_rol_permisos_rol_id ON public.rol_permisos USING btree (rol_id);


--
-- Name: idx_security_policies_categoria; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_security_policies_categoria ON public.security_policies USING btree (categoria);


--
-- Name: idx_security_policies_nombre; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_security_policies_nombre ON public.security_policies USING btree (nombre);


--
-- Name: idx_settings_activo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_settings_activo ON public.notification_settings USING btree (activo);


--
-- Name: idx_settings_canal; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_settings_canal ON public.notification_settings USING btree (canal);


--
-- Name: idx_settings_evento; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_settings_evento ON public.notification_settings USING btree (evento_id);


--
-- Name: idx_solicitud_aprobaciones_aprobador; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_solicitud_aprobaciones_aprobador ON public.solicitud_aprobaciones USING btree (aprobador_id, estado, fecha_limite);


--
-- Name: idx_solicitud_historial_solicitud; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_solicitud_historial_solicitud ON public.solicitud_historial USING btree (solicitud_id, created_at);


--
-- Name: idx_solicitudes_estado; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_solicitudes_estado ON public.solicitudes USING btree (estado, updated_at DESC);


--
-- Name: idx_solicitudes_solicitante; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_solicitudes_solicitante ON public.solicitudes USING btree (solicitante_id, created_at DESC);


--
-- Name: idx_solicitudes_tipo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_solicitudes_tipo ON public.solicitudes USING btree (tipo_id);


--
-- Name: idx_tarea_participante_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tarea_participante_usuario ON public.tarea_participantes_usuario USING btree (usuario_id, tarea_id);


--
-- Name: idx_tareas_asignado_usuario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_tareas_asignado_usuario ON public.tareas USING btree (asignado_usuario_id);


--
-- Name: idx_user_preferences_usuario_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_user_preferences_usuario_id ON public.user_preferences USING btree (usuario_id);


--
-- Name: idx_usuario_roles_rol_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_usuario_roles_rol_id ON public.usuario_roles USING btree (rol_id);


--
-- Name: idx_usuario_roles_usuario_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_usuario_roles_usuario_id ON public.usuario_roles USING btree (usuario_id);


--
-- Name: idx_usuario_unidad_rol_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_usuario_unidad_rol_id ON public.usuario_unidad USING btree (rol_id);


--
-- Name: idx_usuario_unidad_unidad_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_usuario_unidad_unidad_id ON public.usuario_unidad USING btree (unidad_id);


--
-- Name: idx_usuario_unidad_usuario_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_usuario_unidad_usuario_id ON public.usuario_unidad USING btree (usuario_id);


--
-- Name: idx_usuarios_cargo; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_usuarios_cargo ON public.usuarios USING btree (cargo);


--
-- Name: idx_usuarios_codigo_interno; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_usuarios_codigo_interno ON public.usuarios USING btree (codigo_interno);


--
-- Name: idx_usuarios_departamento; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_usuarios_departamento ON public.usuarios USING btree (departamento);


--
-- Name: idx_usuarios_documento; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_usuarios_documento ON public.usuarios USING btree (documento_identidad);


--
-- Name: idx_usuarios_tipo_documento; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_usuarios_tipo_documento ON public.usuarios USING btree (tipo_documento);


--
-- Name: idx_usuarios_username; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_usuarios_username ON public.usuarios USING btree (username);


--
-- Name: ix_admin_password_reset_status_requested; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_admin_password_reset_status_requested ON public.admin_password_reset_requests USING btree (status, requested_at DESC);


--
-- Name: ix_documento_solicitud_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_documento_solicitud_version ON public.documento_solicitudes_aprobacion USING btree (version_id);


--
-- Name: ix_documento_version_historial; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_documento_version_historial ON public.documento_versiones USING btree (documento_id, id DESC);


--
-- Name: ix_reunion_unidades_unidad; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_reunion_unidades_unidad ON public.reunion_unidades USING btree (unidad_id);


--
-- Name: uq_asistencia_invitado_sesion_correo; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_asistencia_invitado_sesion_correo ON public.asistencia_invitados USING btree (sesion_id, lower((correo)::text));


--
-- Name: uq_asistencia_registro_usuario_sesion; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_asistencia_registro_usuario_sesion ON public.asistencia_registros USING btree (usuario_id, sesion_id) WHERE (sesion_id IS NOT NULL);


--
-- Name: uq_documento_creacion; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_documento_creacion ON public.documentos USING btree (propietario_id, creacion_id);


--
-- Name: uq_memo_numeros_numero_ci; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_memo_numeros_numero_ci ON public.memo_numeros USING btree (lower((numero)::text));


--
-- Name: uq_modulos_codigo_ci; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_modulos_codigo_ci ON public.modulos USING btree (lower((codigo)::text)) WHERE (codigo IS NOT NULL);


--
-- Name: uq_modulos_ruta_ci; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_modulos_ruta_ci ON public.modulos USING btree (lower((ruta)::text));


--
-- Name: uq_nota_numero_anual; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_nota_numero_anual ON public.nota_numeros USING btree (anio, consecutivo) WHERE (consecutivo IS NOT NULL);


--
-- Name: uq_nota_numeros_numero_ci; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_nota_numeros_numero_ci ON public.nota_numeros USING btree (lower((numero)::text));


--
-- Name: uq_nota_pdfs_vigente; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_nota_pdfs_vigente ON public.nota_pdfs USING btree (nota_id) WHERE (vigente = true);


--
-- Name: uq_nota_relacion_simetrica; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_nota_relacion_simetrica ON public.nota_relaciones USING btree (LEAST(nota_origen_id, nota_destino_id), GREATEST(nota_origen_id, nota_destino_id), tipo) WHERE ((tipo)::text = 'RELACIONADA_CON'::text);


--
-- Name: uq_organization_departments_nombre; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_organization_departments_nombre ON public.organization_departments USING btree (organization_id, lower(TRIM(BOTH FROM nombre)));


--
-- Name: uq_solicitud_aprobacion_pendiente; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_solicitud_aprobacion_pendiente ON public.solicitud_aprobaciones USING btree (solicitud_id) WHERE ((estado)::text = 'PENDIENTE'::text);


--
-- Name: ux_admin_password_reset_pending; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_admin_password_reset_pending ON public.admin_password_reset_requests USING btree (user_id) WHERE ((status)::text = 'PENDIENTE'::text);


--
-- Name: ux_documento_solicitud_pendiente; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_documento_solicitud_pendiente ON public.documento_solicitudes_aprobacion USING btree (documento_id) WHERE ((estado)::text = 'PENDIENTE'::text);


--
-- Name: ux_documento_version_contenido_numero; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_documento_version_contenido_numero ON public.documento_versiones USING btree (documento_id, numero) WHERE (numero IS NOT NULL);


--
-- Name: ux_tarea_acta_acuerdo; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_tarea_acta_acuerdo ON public.tareas USING btree (acta_id, acuerdo_clave) WHERE ((acta_id IS NOT NULL) AND (acuerdo_clave IS NOT NULL));


--
-- Name: ux_usuarios_username_lower; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_usuarios_username_lower ON public.usuarios USING btree (lower((username)::text));


--
-- Name: acta_acuerdos acta_acuerdos_acta_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acta_acuerdos
    ADD CONSTRAINT acta_acuerdos_acta_id_fkey FOREIGN KEY (acta_id) REFERENCES public.actas(id) ON DELETE CASCADE;


--
-- Name: acta_adjuntos acta_adjuntos_acta_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acta_adjuntos
    ADD CONSTRAINT acta_adjuntos_acta_id_fkey FOREIGN KEY (acta_id) REFERENCES public.actas(id) ON DELETE CASCADE;


--
-- Name: acta_firmas acta_firmas_acta_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acta_firmas
    ADD CONSTRAINT acta_firmas_acta_id_fkey FOREIGN KEY (acta_id) REFERENCES public.actas(id) ON DELETE CASCADE;


--
-- Name: acta_participantes acta_participantes_acta_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.acta_participantes
    ADD CONSTRAINT acta_participantes_acta_id_fkey FOREIGN KEY (acta_id) REFERENCES public.actas(id) ON DELETE CASCADE;


--
-- Name: admin_password_reset_requests admin_password_reset_requests_resolved_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_password_reset_requests
    ADD CONSTRAINT admin_password_reset_requests_resolved_by_fkey FOREIGN KEY (resolved_by) REFERENCES public.usuarios(id) ON DELETE SET NULL;


--
-- Name: admin_password_reset_requests admin_password_reset_requests_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_password_reset_requests
    ADD CONSTRAINT admin_password_reset_requests_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.usuarios(id) ON DELETE CASCADE;


--
-- Name: asistencia_invitados asistencia_invitados_sesion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asistencia_invitados
    ADD CONSTRAINT asistencia_invitados_sesion_id_fkey FOREIGN KEY (sesion_id) REFERENCES public.asistencia_sesiones(id) ON DELETE CASCADE;


--
-- Name: asistencia_registros asistencia_registros_sede_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asistencia_registros
    ADD CONSTRAINT asistencia_registros_sede_id_fkey FOREIGN KEY (sede_id) REFERENCES public.ubicaciones(id) ON DELETE SET NULL;


--
-- Name: asistencia_registros asistencia_registros_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asistencia_registros
    ADD CONSTRAINT asistencia_registros_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE CASCADE;


--
-- Name: asistencia_sesiones asistencia_sesiones_creado_por_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asistencia_sesiones
    ADD CONSTRAINT asistencia_sesiones_creado_por_fkey FOREIGN KEY (creado_por) REFERENCES public.usuarios(id) ON DELETE SET NULL;


--
-- Name: carpeta_documentos carpeta_documentos_carpeta_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carpeta_documentos
    ADD CONSTRAINT carpeta_documentos_carpeta_id_fkey FOREIGN KEY (carpeta_id) REFERENCES public.carpetas(id) ON DELETE CASCADE;


--
-- Name: carpeta_usuarios carpeta_usuarios_carpeta_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carpeta_usuarios
    ADD CONSTRAINT carpeta_usuarios_carpeta_id_fkey FOREIGN KEY (carpeta_id) REFERENCES public.carpetas(id) ON DELETE CASCADE;


--
-- Name: carpeta_usuarios carpeta_usuarios_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carpeta_usuarios
    ADD CONSTRAINT carpeta_usuarios_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE CASCADE;


--
-- Name: carpetas carpetas_propietario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.carpetas
    ADD CONSTRAINT carpetas_propietario_id_fkey FOREIGN KEY (propietario_id) REFERENCES public.usuarios(id);


--
-- Name: documento_solicitudes_aprobacion documento_solicitudes_aprobacion_aprobador_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_solicitudes_aprobacion
    ADD CONSTRAINT documento_solicitudes_aprobacion_aprobador_id_fkey FOREIGN KEY (aprobador_id) REFERENCES public.usuarios(id);


--
-- Name: documento_solicitudes_aprobacion documento_solicitudes_aprobacion_documento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_solicitudes_aprobacion
    ADD CONSTRAINT documento_solicitudes_aprobacion_documento_id_fkey FOREIGN KEY (documento_id) REFERENCES public.documentos(id) ON DELETE CASCADE;


--
-- Name: documento_solicitudes_aprobacion documento_solicitudes_aprobacion_respondido_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_solicitudes_aprobacion
    ADD CONSTRAINT documento_solicitudes_aprobacion_respondido_por_id_fkey FOREIGN KEY (respondido_por_id) REFERENCES public.usuarios(id);


--
-- Name: documento_solicitudes_aprobacion documento_solicitudes_aprobacion_solicitante_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_solicitudes_aprobacion
    ADD CONSTRAINT documento_solicitudes_aprobacion_solicitante_id_fkey FOREIGN KEY (solicitante_id) REFERENCES public.usuarios(id);


--
-- Name: documento_solicitudes_aprobacion documento_solicitudes_aprobacion_version_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_solicitudes_aprobacion
    ADD CONSTRAINT documento_solicitudes_aprobacion_version_id_fkey FOREIGN KEY (version_id) REFERENCES public.documento_versiones(id);


--
-- Name: documento_usuarios documento_usuarios_creado_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_usuarios
    ADD CONSTRAINT documento_usuarios_creado_por_id_fkey FOREIGN KEY (creado_por_id) REFERENCES public.usuarios(id);


--
-- Name: documento_usuarios documento_usuarios_documento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_usuarios
    ADD CONSTRAINT documento_usuarios_documento_id_fkey FOREIGN KEY (documento_id) REFERENCES public.documentos(id) ON DELETE CASCADE;


--
-- Name: documento_usuarios documento_usuarios_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_usuarios
    ADD CONSTRAINT documento_usuarios_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE CASCADE;


--
-- Name: documento_versiones documento_versiones_autor_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_versiones
    ADD CONSTRAINT documento_versiones_autor_id_fkey FOREIGN KEY (autor_id) REFERENCES public.usuarios(id) ON DELETE SET NULL;


--
-- Name: documento_versiones documento_versiones_documento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documento_versiones
    ADD CONSTRAINT documento_versiones_documento_id_fkey FOREIGN KEY (documento_id) REFERENCES public.documentos(id) ON DELETE CASCADE;


--
-- Name: documentos documentos_carpeta_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documentos
    ADD CONSTRAINT documentos_carpeta_id_fkey FOREIGN KEY (carpeta_id) REFERENCES public.carpetas(id) ON DELETE SET NULL;


--
-- Name: documentos documentos_propietario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documentos
    ADD CONSTRAINT documentos_propietario_id_fkey FOREIGN KEY (propietario_id) REFERENCES public.usuarios(id) ON DELETE SET NULL;


--
-- Name: documentos documentos_version_actual_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.documentos
    ADD CONSTRAINT documentos_version_actual_id_fkey FOREIGN KEY (version_actual_id) REFERENCES public.documento_versiones(id);


--
-- Name: actas fk_acta_sesion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.actas
    ADD CONSTRAINT fk_acta_sesion FOREIGN KEY (sesion_id) REFERENCES public.asistencia_sesiones(id);


--
-- Name: modulos fk_modulos_padre; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.modulos
    ADD CONSTRAINT fk_modulos_padre FOREIGN KEY (padre_id) REFERENCES public.modulos(id) ON DELETE SET NULL;


--
-- Name: parametros fk_parametros_modulo; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parametros
    ADD CONSTRAINT fk_parametros_modulo FOREIGN KEY (modulo_id) REFERENCES public.modulos(id) ON DELETE SET NULL;


--
-- Name: asistencia_registros fk_registro_sesion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.asistencia_registros
    ADD CONSTRAINT fk_registro_sesion FOREIGN KEY (sesion_id) REFERENCES public.asistencia_sesiones(id);


--
-- Name: reuniones fk_reuniones_organizador_usuario; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reuniones
    ADD CONSTRAINT fk_reuniones_organizador_usuario FOREIGN KEY (organizador_usuario_id) REFERENCES public.usuarios(id) ON DELETE SET NULL;


--
-- Name: tareas fk_tareas_asignado_usuario; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tareas
    ADD CONSTRAINT fk_tareas_asignado_usuario FOREIGN KEY (asignado_usuario_id) REFERENCES public.usuarios(id) ON DELETE SET NULL;


--
-- Name: user_preferences fk_user_preferences_usuario; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_preferences
    ADD CONSTRAINT fk_user_preferences_usuario FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE CASCADE;


--
-- Name: grupo_mensajes grupo_mensajes_autor_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grupo_mensajes
    ADD CONSTRAINT grupo_mensajes_autor_id_fkey FOREIGN KEY (autor_id) REFERENCES public.usuarios(id);


--
-- Name: grupo_mensajes grupo_mensajes_grupo_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grupo_mensajes
    ADD CONSTRAINT grupo_mensajes_grupo_id_fkey FOREIGN KEY (grupo_id) REFERENCES public.grupos(id) ON DELETE CASCADE;


--
-- Name: grupo_permisos grupo_permisos_grupo_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grupo_permisos
    ADD CONSTRAINT grupo_permisos_grupo_id_fkey FOREIGN KEY (grupo_id) REFERENCES public.grupos(id) ON DELETE CASCADE;


--
-- Name: grupo_permisos grupo_permisos_permiso_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grupo_permisos
    ADD CONSTRAINT grupo_permisos_permiso_id_fkey FOREIGN KEY (permiso_id) REFERENCES public.permisos(id);


--
-- Name: grupo_usuarios grupo_usuarios_grupo_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grupo_usuarios
    ADD CONSTRAINT grupo_usuarios_grupo_id_fkey FOREIGN KEY (grupo_id) REFERENCES public.grupos(id) ON DELETE CASCADE;


--
-- Name: grupo_usuarios grupo_usuarios_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grupo_usuarios
    ADD CONSTRAINT grupo_usuarios_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id);


--
-- Name: grupos grupos_creado_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grupos
    ADD CONSTRAINT grupos_creado_por_id_fkey FOREIGN KEY (creado_por_id) REFERENCES public.usuarios(id);


--
-- Name: grupos grupos_unidad_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grupos
    ADD CONSTRAINT grupos_unidad_id_fkey FOREIGN KEY (unidad_id) REFERENCES public.unidades(id);


--
-- Name: horarios horarios_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.horarios
    ADD CONSTRAINT horarios_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE CASCADE;


--
-- Name: inventario_activos inventario_activos_asignado_unidad_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventario_activos
    ADD CONSTRAINT inventario_activos_asignado_unidad_id_fkey FOREIGN KEY (asignado_unidad_id) REFERENCES public.org_units(id);


--
-- Name: inventario_activos inventario_activos_asignado_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventario_activos
    ADD CONSTRAINT inventario_activos_asignado_usuario_id_fkey FOREIGN KEY (asignado_usuario_id) REFERENCES public.usuarios(id);


--
-- Name: memo_historial memo_historial_memo_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.memo_historial
    ADD CONSTRAINT memo_historial_memo_id_fkey FOREIGN KEY (memo_id) REFERENCES public.memos(id) ON DELETE CASCADE;


--
-- Name: memo_historial memo_historial_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.memo_historial
    ADD CONSTRAINT memo_historial_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE SET NULL;


--
-- Name: memo_numeros memo_numeros_reservado_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.memo_numeros
    ADD CONSTRAINT memo_numeros_reservado_por_id_fkey FOREIGN KEY (reservado_por_id) REFERENCES public.usuarios(id) ON DELETE SET NULL;


--
-- Name: memos memos_anulado_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.memos
    ADD CONSTRAINT memos_anulado_por_id_fkey FOREIGN KEY (anulado_por_id) REFERENCES public.usuarios(id);


--
-- Name: memos memos_completado_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.memos
    ADD CONSTRAINT memos_completado_por_id_fkey FOREIGN KEY (completado_por_id) REFERENCES public.usuarios(id);


--
-- Name: memos memos_creado_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.memos
    ADD CONSTRAINT memos_creado_por_id_fkey FOREIGN KEY (creado_por_id) REFERENCES public.usuarios(id);


--
-- Name: memos memos_numero_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.memos
    ADD CONSTRAINT memos_numero_id_fkey FOREIGN KEY (numero_id) REFERENCES public.memo_numeros(id);


--
-- Name: memos memos_seguimiento_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.memos
    ADD CONSTRAINT memos_seguimiento_usuario_id_fkey FOREIGN KEY (seguimiento_usuario_id) REFERENCES public.usuarios(id);


--
-- Name: nota_historial nota_historial_nota_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.nota_historial
    ADD CONSTRAINT nota_historial_nota_id_fkey FOREIGN KEY (nota_id) REFERENCES public.notas(id) ON DELETE CASCADE;


--
-- Name: nota_historial nota_historial_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.nota_historial
    ADD CONSTRAINT nota_historial_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE SET NULL;


--
-- Name: nota_numeros nota_numeros_reservado_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.nota_numeros
    ADD CONSTRAINT nota_numeros_reservado_por_id_fkey FOREIGN KEY (reservado_por_id) REFERENCES public.usuarios(id);


--
-- Name: nota_pdfs nota_pdfs_eliminado_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.nota_pdfs
    ADD CONSTRAINT nota_pdfs_eliminado_por_id_fkey FOREIGN KEY (eliminado_por_id) REFERENCES public.usuarios(id);


--
-- Name: nota_pdfs nota_pdfs_nota_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.nota_pdfs
    ADD CONSTRAINT nota_pdfs_nota_id_fkey FOREIGN KEY (nota_id) REFERENCES public.notas(id) ON DELETE CASCADE;


--
-- Name: nota_pdfs nota_pdfs_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.nota_pdfs
    ADD CONSTRAINT nota_pdfs_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE SET NULL;


--
-- Name: nota_relaciones nota_relaciones_creado_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.nota_relaciones
    ADD CONSTRAINT nota_relaciones_creado_por_id_fkey FOREIGN KEY (creado_por_id) REFERENCES public.usuarios(id) ON DELETE SET NULL;


--
-- Name: nota_relaciones nota_relaciones_nota_destino_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.nota_relaciones
    ADD CONSTRAINT nota_relaciones_nota_destino_id_fkey FOREIGN KEY (nota_destino_id) REFERENCES public.notas(id) ON DELETE CASCADE;


--
-- Name: nota_relaciones nota_relaciones_nota_origen_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.nota_relaciones
    ADD CONSTRAINT nota_relaciones_nota_origen_id_fkey FOREIGN KEY (nota_origen_id) REFERENCES public.notas(id) ON DELETE CASCADE;


--
-- Name: notas notas_anulada_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notas
    ADD CONSTRAINT notas_anulada_por_id_fkey FOREIGN KEY (anulada_por_id) REFERENCES public.usuarios(id);


--
-- Name: notas notas_cerrada_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notas
    ADD CONSTRAINT notas_cerrada_por_id_fkey FOREIGN KEY (cerrada_por_id) REFERENCES public.usuarios(id) ON DELETE SET NULL;


--
-- Name: notas notas_creado_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notas
    ADD CONSTRAINT notas_creado_por_id_fkey FOREIGN KEY (creado_por_id) REFERENCES public.usuarios(id);


--
-- Name: notas notas_departamento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notas
    ADD CONSTRAINT notas_departamento_id_fkey FOREIGN KEY (departamento_id) REFERENCES public.organization_departments(id) ON DELETE RESTRICT;


--
-- Name: notas notas_firmada_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notas
    ADD CONSTRAINT notas_firmada_por_id_fkey FOREIGN KEY (firmada_por_id) REFERENCES public.usuarios(id) ON DELETE SET NULL;


--
-- Name: notas notas_firmante_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notas
    ADD CONSTRAINT notas_firmante_id_fkey FOREIGN KEY (firmante_id) REFERENCES public.usuarios(id) ON DELETE SET NULL;


--
-- Name: notas notas_institucion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notas
    ADD CONSTRAINT notas_institucion_id_fkey FOREIGN KEY (institucion_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: notas notas_numero_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notas
    ADD CONSTRAINT notas_numero_id_fkey FOREIGN KEY (numero_id) REFERENCES public.nota_numeros(id);


--
-- Name: notas notas_oficializada_por_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notas
    ADD CONSTRAINT notas_oficializada_por_id_fkey FOREIGN KEY (oficializada_por_id) REFERENCES public.usuarios(id) ON DELETE SET NULL;


--
-- Name: notas notas_seguimiento_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notas
    ADD CONSTRAINT notas_seguimiento_usuario_id_fkey FOREIGN KEY (seguimiento_usuario_id) REFERENCES public.usuarios(id);


--
-- Name: notificaciones notificaciones_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notificaciones
    ADD CONSTRAINT notificaciones_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE CASCADE;


--
-- Name: notification_recipients notification_recipients_evento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_recipients
    ADD CONSTRAINT notification_recipients_evento_id_fkey FOREIGN KEY (evento_id) REFERENCES public.notification_events(id) ON DELETE CASCADE;


--
-- Name: notification_recipients notification_recipients_rol_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_recipients
    ADD CONSTRAINT notification_recipients_rol_id_fkey FOREIGN KEY (rol_id) REFERENCES public.roles(id) ON DELETE CASCADE;


--
-- Name: notification_settings notification_settings_evento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_settings
    ADD CONSTRAINT notification_settings_evento_id_fkey FOREIGN KEY (evento_id) REFERENCES public.notification_events(id) ON DELETE CASCADE;


--
-- Name: notification_settings notification_settings_plantilla_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_settings
    ADD CONSTRAINT notification_settings_plantilla_id_fkey FOREIGN KEY (plantilla_id) REFERENCES public.notification_templates(id) ON DELETE SET NULL;


--
-- Name: notification_templates notification_templates_evento_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_templates
    ADD CONSTRAINT notification_templates_evento_id_fkey FOREIGN KEY (evento_id) REFERENCES public.notification_events(id) ON DELETE SET NULL;


--
-- Name: org_units org_units_padre_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.org_units
    ADD CONSTRAINT org_units_padre_id_fkey FOREIGN KEY (padre_id) REFERENCES public.org_units(id) ON DELETE SET NULL;


--
-- Name: organization_departments organization_departments_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization_departments
    ADD CONSTRAINT organization_departments_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE RESTRICT;


--
-- Name: organization_settings organization_settings_organization_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.organization_settings
    ADD CONSTRAINT organization_settings_organization_id_fkey FOREIGN KEY (organization_id) REFERENCES public.organizations(id) ON DELETE CASCADE;


--
-- Name: parameter_history parameter_history_parametro_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.parameter_history
    ADD CONSTRAINT parameter_history_parametro_id_fkey FOREIGN KEY (parametro_id) REFERENCES public.parametros(id) ON DELETE CASCADE;


--
-- Name: password_history password_history_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_history
    ADD CONSTRAINT password_history_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE CASCADE;


--
-- Name: password_reset_requests password_reset_requests_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_requests
    ADD CONSTRAINT password_reset_requests_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.usuarios(id) ON DELETE CASCADE;


--
-- Name: reportes reportes_creado_por_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reportes
    ADD CONSTRAINT reportes_creado_por_usuario_id_fkey FOREIGN KEY (creado_por_usuario_id) REFERENCES public.usuarios(id) ON DELETE SET NULL;


--
-- Name: reunion_participantes_usuario reunion_participantes_usuario_reunion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reunion_participantes_usuario
    ADD CONSTRAINT reunion_participantes_usuario_reunion_id_fkey FOREIGN KEY (reunion_id) REFERENCES public.reuniones(id) ON DELETE CASCADE;


--
-- Name: reunion_participantes_usuario reunion_participantes_usuario_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reunion_participantes_usuario
    ADD CONSTRAINT reunion_participantes_usuario_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE CASCADE;


--
-- Name: reunion_unidades reunion_unidades_reunion_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reunion_unidades
    ADD CONSTRAINT reunion_unidades_reunion_id_fkey FOREIGN KEY (reunion_id) REFERENCES public.reuniones(id) ON DELETE CASCADE;


--
-- Name: reunion_unidades reunion_unidades_unidad_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.reunion_unidades
    ADD CONSTRAINT reunion_unidades_unidad_id_fkey FOREIGN KEY (unidad_id) REFERENCES public.unidades(id);


--
-- Name: rol_permisos rol_permisos_permiso_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rol_permisos
    ADD CONSTRAINT rol_permisos_permiso_id_fkey FOREIGN KEY (permiso_id) REFERENCES public.permisos(id) ON DELETE CASCADE;


--
-- Name: rol_permisos rol_permisos_rol_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rol_permisos
    ADD CONSTRAINT rol_permisos_rol_id_fkey FOREIGN KEY (rol_id) REFERENCES public.roles(id) ON DELETE CASCADE;


--
-- Name: solicitud_aprobaciones solicitud_aprobaciones_aprobador_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_aprobaciones
    ADD CONSTRAINT solicitud_aprobaciones_aprobador_id_fkey FOREIGN KEY (aprobador_id) REFERENCES public.usuarios(id);


--
-- Name: solicitud_aprobaciones solicitud_aprobaciones_escalada_a_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_aprobaciones
    ADD CONSTRAINT solicitud_aprobaciones_escalada_a_id_fkey FOREIGN KEY (escalada_a_id) REFERENCES public.usuarios(id) ON DELETE SET NULL;


--
-- Name: solicitud_aprobaciones solicitud_aprobaciones_etapa_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_aprobaciones
    ADD CONSTRAINT solicitud_aprobaciones_etapa_id_fkey FOREIGN KEY (etapa_id) REFERENCES public.solicitud_etapas(id);


--
-- Name: solicitud_aprobaciones solicitud_aprobaciones_solicitud_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_aprobaciones
    ADD CONSTRAINT solicitud_aprobaciones_solicitud_id_fkey FOREIGN KEY (solicitud_id) REFERENCES public.solicitudes(id) ON DELETE CASCADE;


--
-- Name: solicitud_etapas solicitud_etapas_aprobador_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_etapas
    ADD CONSTRAINT solicitud_etapas_aprobador_usuario_id_fkey FOREIGN KEY (aprobador_usuario_id) REFERENCES public.usuarios(id) ON DELETE SET NULL;


--
-- Name: solicitud_etapas solicitud_etapas_tipo_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_etapas
    ADD CONSTRAINT solicitud_etapas_tipo_id_fkey FOREIGN KEY (tipo_id) REFERENCES public.solicitud_tipos(id) ON DELETE CASCADE;


--
-- Name: solicitud_historial solicitud_historial_solicitud_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_historial
    ADD CONSTRAINT solicitud_historial_solicitud_id_fkey FOREIGN KEY (solicitud_id) REFERENCES public.solicitudes(id) ON DELETE CASCADE;


--
-- Name: solicitud_historial solicitud_historial_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitud_historial
    ADD CONSTRAINT solicitud_historial_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE SET NULL;


--
-- Name: solicitudes solicitudes_etapa_actual_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitudes
    ADD CONSTRAINT solicitudes_etapa_actual_id_fkey FOREIGN KEY (etapa_actual_id) REFERENCES public.solicitud_etapas(id);


--
-- Name: solicitudes solicitudes_solicitante_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitudes
    ADD CONSTRAINT solicitudes_solicitante_id_fkey FOREIGN KEY (solicitante_id) REFERENCES public.usuarios(id);


--
-- Name: solicitudes solicitudes_tipo_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.solicitudes
    ADD CONSTRAINT solicitudes_tipo_id_fkey FOREIGN KEY (tipo_id) REFERENCES public.solicitud_tipos(id);


--
-- Name: tarea_adjuntos tarea_adjuntos_tarea_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tarea_adjuntos
    ADD CONSTRAINT tarea_adjuntos_tarea_id_fkey FOREIGN KEY (tarea_id) REFERENCES public.tareas(id) ON DELETE CASCADE;


--
-- Name: tarea_checklist tarea_checklist_tarea_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tarea_checklist
    ADD CONSTRAINT tarea_checklist_tarea_id_fkey FOREIGN KEY (tarea_id) REFERENCES public.tareas(id) ON DELETE CASCADE;


--
-- Name: tarea_participantes_usuario tarea_participantes_usuario_tarea_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tarea_participantes_usuario
    ADD CONSTRAINT tarea_participantes_usuario_tarea_id_fkey FOREIGN KEY (tarea_id) REFERENCES public.tareas(id) ON DELETE CASCADE;


--
-- Name: tarea_participantes_usuario tarea_participantes_usuario_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tarea_participantes_usuario
    ADD CONSTRAINT tarea_participantes_usuario_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE CASCADE;


--
-- Name: tareas tareas_acta_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tareas
    ADD CONSTRAINT tareas_acta_id_fkey FOREIGN KEY (acta_id) REFERENCES public.actas(id) ON DELETE SET NULL;


--
-- Name: usuario_roles usuario_roles_rol_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_roles
    ADD CONSTRAINT usuario_roles_rol_id_fkey FOREIGN KEY (rol_id) REFERENCES public.roles(id) ON DELETE CASCADE;


--
-- Name: usuario_roles usuario_roles_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_roles
    ADD CONSTRAINT usuario_roles_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE CASCADE;


--
-- Name: usuario_unidad usuario_unidad_rol_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_unidad
    ADD CONSTRAINT usuario_unidad_rol_id_fkey FOREIGN KEY (rol_id) REFERENCES public.roles(id) ON DELETE CASCADE;


--
-- Name: usuario_unidad usuario_unidad_unidad_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_unidad
    ADD CONSTRAINT usuario_unidad_unidad_id_fkey FOREIGN KEY (unidad_id) REFERENCES public.unidades(id) ON DELETE CASCADE;


--
-- Name: usuario_unidad usuario_unidad_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuario_unidad
    ADD CONSTRAINT usuario_unidad_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE CASCADE;


--
-- Name: usuarios_2fa usuarios_2fa_usuario_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.usuarios_2fa
    ADD CONSTRAINT usuarios_2fa_usuario_id_fkey FOREIGN KEY (usuario_id) REFERENCES public.usuarios(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--



-- Privilegios de produccion: se aplican solo cuando los roles tecnicos existen.
-- Las funciones nuevas no son ejecutables por PUBLIC. Cada funcion de negocio
-- debe concederse de forma explicita tras revisar que no sea SECURITY DEFINER.
ALTER DEFAULT PRIVILEGES REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

DO $production_grants$
DECLARE f record;
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sigaa_runtime') THEN
        REVOKE ALL ON SCHEMA public FROM PUBLIC;
        GRANT USAGE ON SCHEMA public TO sigaa_runtime;
        GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO sigaa_runtime;
        GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO sigaa_runtime;
        REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA public FROM PUBLIC, sigaa_runtime;
        FOR f IN
            SELECT p.oid::regprocedure AS signature
            FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
            WHERE n.nspname = 'public' AND p.prokind = 'f' AND NOT p.prosecdef
        LOOP
            EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO sigaa_runtime', f.signature);
        END LOOP;
        EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO sigaa_runtime';
        EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO sigaa_runtime';
        REVOKE CREATE ON SCHEMA public FROM sigaa_runtime;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sigaa_backup') THEN
        GRANT USAGE ON SCHEMA public TO sigaa_backup;
        GRANT SELECT ON ALL TABLES IN SCHEMA public TO sigaa_backup;
        GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO sigaa_backup;
        EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO sigaa_backup';
        EXECUTE 'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON SEQUENCES TO sigaa_backup';
        REVOKE CREATE ON SCHEMA public FROM sigaa_backup;
    END IF;
END
$production_grants$;

COMMIT;
