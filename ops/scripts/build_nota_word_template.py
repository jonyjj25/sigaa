from __future__ import annotations

import shutil
import tempfile
import zipfile
from copy import deepcopy
from pathlib import Path

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.style import WD_STYLE_TYPE
from docx.oxml.ns import qn
from docx.shared import Inches, Pt
from lxml import etree


W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
NS = {"w": W_NS}
TAGS_BLOQUEADOS = {
    "FECHA_OFICIAL",
    "NUMERO_NOTA",
    "DESTINATARIO_NOMBRE",
    "DESTINATARIO_CARGO",
    "INSTITUCION",
    "FIRMANTE_NOMBRE",
    "FIRMANTE_CARGO",
    "FIRMANTE_INSTITUCION",
    "INICIALES",
}


def w(local: str) -> str:
    return f"{{{W_NS}}}{local}"


def aplicar_fuente(run, nombre="Verdana", tamano=11, negrita=False):
    run.font.name = nombre
    run.font.size = Pt(tamano)
    run.bold = negrita
    run._element.get_or_add_rPr().rFonts.set(qn("w:ascii"), nombre)
    run._element.get_or_add_rPr().rFonts.set(qn("w:hAnsi"), nombre)


def agregar_linea(doc: Document, texto: str, *, alineacion=None, negrita=False, despues=0, antes=0):
    p = doc.add_paragraph()
    if alineacion is not None:
        p.alignment = alineacion
    p.paragraph_format.space_before = Pt(antes)
    p.paragraph_format.space_after = Pt(despues)
    p.paragraph_format.line_spacing = 1.0
    aplicar_fuente(p.add_run(texto), negrita=negrita)
    return p


def crear_base(destino: Path):
    doc = Document()
    section = doc.sections[0]
    section.page_width = Inches(8.5)
    section.page_height = Inches(11)
    section.top_margin = Inches(0.8)
    section.bottom_margin = Inches(0.75)
    section.left_margin = Inches(1.0)
    section.right_margin = Inches(1.0)

    normal = doc.styles["Normal"]
    normal.font.name = "Verdana"
    normal.font.size = Pt(11)
    normal.paragraph_format.space_before = Pt(0)
    normal.paragraph_format.space_after = Pt(0)
    normal.paragraph_format.line_spacing = 1.0
    normal.element.get_or_add_rPr().get_or_add_rFonts().set(qn("w:ascii"), "Verdana")
    normal.element.get_or_add_rPr().get_or_add_rFonts().set(qn("w:hAnsi"), "Verdana")
    body = doc.styles.add_style("NotaContenido", WD_STYLE_TYPE.PARAGRAPH)
    body.base_style = normal
    body.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    body.paragraph_format.space_after = Pt(11)
    body.paragraph_format.widow_control = True

    header = section.header
    hp = header.paragraphs[0]
    hp.alignment = WD_ALIGN_PARAGRAPH.CENTER
    aplicar_fuente(hp.add_run("SIGAA"), tamano=13, negrita=True)
    hp.paragraph_format.space_after = Pt(2)
    hp2 = header.add_paragraph()
    hp2.alignment = WD_ALIGN_PARAGRAPH.CENTER
    aplicar_fuente(hp2.add_run("Nota institucional"), tamano=9)

    agregar_linea(doc, "{{FECHA_OFICIAL}}", alineacion=WD_ALIGN_PARAGRAPH.RIGHT)
    agregar_linea(doc, "Nota. N° {{NUMERO_NOTA}}", alineacion=WD_ALIGN_PARAGRAPH.RIGHT, negrita=True, despues=11)

    agregar_linea(doc, "{{DESTINATARIO_NOMBRE}}", negrita=True)
    agregar_linea(doc, "{{DESTINATARIO_CARGO}}", negrita=True)
    agregar_linea(doc, "{{INSTITUCION}}", despues=11)

    content = agregar_linea(doc, "{{CONTENIDO_NOTA}}", alineacion=WD_ALIGN_PARAGRAPH.JUSTIFY, despues=11)
    content.style = body

    # 54 puntos (1.9 cm) libres para la firma manuscrita; todo el bloque permanece unido.
    signature = [agregar_linea(doc, "ATENTAMENTE,", despues=54),
        agregar_linea(doc, "{{FIRMANTE_CARGO}}", negrita=True),
        agregar_linea(doc, "{{FIRMANTE_NOMBRE}}", negrita=True),
        agregar_linea(doc, "{{FIRMANTE_INSTITUCION}}"),
        agregar_linea(doc, "Sistema Nacional de AA", despues=11)]
    for paragraph in signature:
        paragraph.paragraph_format.keep_with_next = True
    initials = agregar_linea(doc, "{{INICIALES}}")
    for run in initials.runs:
        aplicar_fuente(run, tamano=8)

    footer = section.footer
    fp = footer.paragraphs[0]
    fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
    aplicar_fuente(fp.add_run("Documento generado por SIGAA"), tamano=8)

    doc.save(destino)


def convertir_controles_y_proteger(origen: Path, destino: Path):
    with tempfile.TemporaryDirectory(prefix="nota_template_") as temp:
        carpeta = Path(temp)
        with zipfile.ZipFile(origen, "r") as archivo:
            archivo.extractall(carpeta)

        document_path = carpeta / "word" / "document.xml"
        parser = etree.XMLParser(remove_blank_text=False)
        tree = etree.parse(str(document_path), parser)
        root = tree.getroot()

        for run in list(root.xpath(".//w:r", namespaces=NS)):
            textos = run.xpath("./w:t", namespaces=NS)
            texto = "".join(item.text or "" for item in textos)
            if "{{" not in texto or "}}" not in texto:
                continue
            inicio = texto.find("{{")
            fin = texto.find("}}", inicio)
            if inicio < 0 or fin < 0:
                continue
            tag = texto[inicio + 2:fin]
            if tag not in TAGS_BLOQUEADOS and tag != "CONTENIDO_NOTA":
                continue
            padre = run.getparent()
            indice = padre.index(run)
            sdt = etree.Element(w("sdt"))
            props = etree.SubElement(sdt, w("sdtPr"))
            alias = etree.SubElement(props, w("alias"))
            alias.set(w("val"), tag)
            tag_el = etree.SubElement(props, w("tag"))
            tag_el.set(w("val"), tag)
            if tag != "CONTENIDO_NOTA":
                etree.SubElement(props, w("text"))
            if tag in TAGS_BLOQUEADOS:
                lock = etree.SubElement(props, w("lock"))
                lock.set(w("val"), "sdtContentLocked")
            contenido = etree.SubElement(sdt, w("sdtContent"))
            if tag == "CONTENIDO_NOTA":
                # Control de bloque: párrafos reales editables, sin w:br que
                # estiren la última línea de cada párrafo al justificar.
                contenido.append(deepcopy(padre))
                abuelo = padre.getparent()
                abuelo.insert(abuelo.index(padre), sdt)
                abuelo.remove(padre)
            else:
                contenido.append(deepcopy(run))
                padre.insert(indice, sdt)
                padre.remove(run)

        tree.write(str(document_path), xml_declaration=True, encoding="UTF-8", standalone="yes")

        settings_path = carpeta / "word" / "settings.xml"
        settings_tree = etree.parse(str(settings_path), parser)
        settings_root = settings_tree.getroot()
        for anterior in settings_root.xpath("./w:documentProtection", namespaces=NS):
            settings_root.remove(anterior)
        protection = etree.Element(w("documentProtection"))
        protection.set(w("edit"), "forms")
        protection.set(w("enforcement"), "1")
        settings_root.insert(0, protection)
        settings_tree.write(str(settings_path), xml_declaration=True, encoding="UTF-8", standalone="yes")

        destino.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(destino, "w", zipfile.ZIP_DEFLATED) as archivo:
            for path in carpeta.rglob("*"):
                if path.is_file():
                    archivo.write(path, path.relative_to(carpeta).as_posix())


def main():
    raiz = Path(__file__).resolve().parents[2]
    salida = raiz / "backend" / "sigaa-api" / "src" / "main" / "resources" / "templates" / "correspondencia" / "plantilla-nota.docx"
    with tempfile.TemporaryDirectory(prefix="nota_base_") as temp:
        base = Path(temp) / "base.docx"
        crear_base(base)
        convertir_controles_y_proteger(base, salida)
    print(salida)


if __name__ == "__main__":
    main()
