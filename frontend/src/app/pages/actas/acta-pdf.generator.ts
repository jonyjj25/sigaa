import jsPDF from 'jspdf';
import { Acta, ActaAcuerdo } from './actas.models';

export interface ActaPdfAssets {
  headerBar: string;
  logo: string;
  footerBar: string;
}

export interface ActaPdfItem {
  descripcion: string;
  responsable: string;
  estado: string;
}

const COMMITMENT_PREFIX = /^\[Compromiso\]\s*/i;

export function normalizePdfText(value: unknown): string {
  return String(value ?? '')
    .normalize('NFKC')
    .replace(/\r\n?/g, '\n')
    .replace(/[\u2022\u2023\u2043\u2219\u25AA\u25CF\u25E6\uF0B7\uF076]/g, '-')
    .replace(/[\u2010\u2011\u2012\u2013\u2014\u2212]/g, '-')
    .replace(/[\u2018\u2019\u201A\u201B]/g, "'")
    .replace(/[\u201C\u201D\u201E\u201F]/g, '"')
    .replace(/\u2026/g, '...')
    .replace(/[\uE000-\uF8FF]/g, '-')
    .replace(/[^\t\n\x20-\x7E\u00A0-\u00FF]/g, '')
    .split('\n')
    .map(line => line.replace(/[\t ]+/g, ' ').trim())
    .join('\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

export function splitActaPdfItems(items: ActaAcuerdo[]): {
  acuerdos: ActaPdfItem[];
  compromisos: ActaPdfItem[];
} {
  const acuerdos: ActaPdfItem[] = [];
  const compromisos: ActaPdfItem[] = [];

  for (const item of items) {
    const rawDescription = normalizePdfText(item.descripcion);
    if (!rawDescription) continue;

    const isCommitment = COMMITMENT_PREFIX.test(rawDescription);
    const normalizedItem: ActaPdfItem = {
      descripcion: rawDescription.replace(COMMITMENT_PREFIX, '').trim(),
      responsable: normalizePdfText(item.responsable),
      estado: normalizePdfText(item.estado),
    };

    (isCommitment ? compromisos : acuerdos).push(normalizedItem);
  }

  return { acuerdos, compromisos };
}

export function buildActaPdf(acta: Acta, assets: ActaPdfAssets): jsPDF {
  const MARGIN_TOP = 29;
  const MARGIN_LEFT = 30;
  const MARGIN_RIGHT = 30;
  const PAGE_W = 215.9;
  const PAGE_H = 279.4;
  const HEADER_H = 23.47;
  const LOGO_W = 20.64;
  const LOGO_H = 12.78;
  const FOOTER_H = 24.04;
  const USABLE_W = PAGE_W - MARGIN_LEFT - MARGIN_RIGHT;
  const CONTENT_BOTTOM = PAGE_H - FOOTER_H - 6;
  const CONTENT_HEIGHT = CONTENT_BOTTOM - MARGIN_TOP;
  const BODY_LINE_H = 4.25;
  const TEXT_WRAP_SAFETY = 1.5;

  const doc = new jsPDF('p', 'mm', 'letter');

  const drawBackground = () => {
    doc.addImage(assets.headerBar, 'PNG', 0, 0, PAGE_W, HEADER_H);
    doc.addImage(assets.logo, 'PNG', PAGE_W - MARGIN_RIGHT - LOGO_W, 5.5, LOGO_W, LOGO_H);
    doc.addImage(assets.footerBar, 'PNG', 0, PAGE_H - FOOTER_H, PAGE_W, FOOTER_H);
  };

  drawBackground();
  let currentY = MARGIN_TOP;

  const addPage = () => {
    doc.addPage();
    drawBackground();
    currentY = MARGIN_TOP;
  };

  const ensureSpace = (needed: number) => {
    if (currentY + needed > CONTENT_BOTTOM) addPage();
  };

  const splitLines = (text: unknown, width: number): string[] => {
    const normalized = normalizePdfText(text);
    if (!normalized) return [];
    return doc.splitTextToSize(normalized, Math.max(width - TEXT_WRAP_SAFETY, 10)) as string[];
  };

  const setBodyStyle = (size = 9, style: 'normal' | 'bold' | 'italic' = 'normal') => {
    doc.setFont('Helvetica', style);
    doc.setFontSize(size);
    doc.setTextColor(51, 65, 85);
  };

  const writeWrappedLines = (
    lines: string[],
    x: number,
    lineHeight: number,
    keepTogether = true,
  ) => {
    if (lines.length === 0) return;
    const blockHeight = lines.length * lineHeight;
    if (keepTogether && blockHeight <= CONTENT_HEIGHT) ensureSpace(blockHeight);

    for (const line of lines) {
      ensureSpace(lineHeight);
      doc.text(line, x, currentY);
      currentY += lineHeight;
    }
  };

  const sectionTitle = (title: string) => {
    ensureSpace(11);
    doc.setFont('Helvetica', 'bold');
    doc.setFontSize(10.5);
    doc.setTextColor(30, 64, 175);
    doc.text(normalizePdfText(title), MARGIN_LEFT, currentY);
    currentY += 2;
    doc.setDrawColor(226, 232, 240);
    doc.line(MARGIN_LEFT, currentY, MARGIN_LEFT + USABLE_W, currentY);
    currentY += 6;
  };

  const mutedText = (text: string) => {
    doc.setFont('Helvetica', 'italic');
    doc.setFontSize(8.5);
    doc.setTextColor(100, 116, 139);
    writeWrappedLines(splitLines(text, USABLE_W), MARGIN_LEFT, BODY_LINE_H);
    currentY += 2;
  };

  const bodyText = (text: string) => {
    const normalized = normalizePdfText(text);
    if (!normalized) {
      mutedText('Sin información registrada.');
      return;
    }

    setBodyStyle();
    const paragraphs = normalized.split('\n');
    for (const paragraph of paragraphs) {
      if (!paragraph) {
        ensureSpace(2);
        currentY += 2;
        continue;
      }
      writeWrappedLines(splitLines(paragraph, USABLE_W), MARGIN_LEFT, BODY_LINE_H);
    }
    currentY += 2;
  };

  const listItem = (text: string, marker = '-') => {
    const indent = 6;
    setBodyStyle();
    const lines = splitLines(text, USABLE_W - indent);
    if (lines.length === 0) return;

    const itemHeight = lines.length * BODY_LINE_H + 1;
    if (itemHeight <= CONTENT_HEIGHT) ensureSpace(itemHeight);
    doc.text(normalizePdfText(marker), MARGIN_LEFT, currentY);
    writeWrappedLines(lines, MARGIN_LEFT + indent, BODY_LINE_H, false);
    currentY += 1;
  };

  const infoLine = (label: string, value: unknown) => {
    const labelWidth = 29;
    const valueX = MARGIN_LEFT + labelWidth;
    doc.setFont('Helvetica', 'normal');
    doc.setFontSize(8.5);
    const lines = splitLines(value || '-', USABLE_W - labelWidth);
    const lineCount = Math.max(1, lines.length);
    const height = lineCount * 4.25;
    ensureSpace(height);

    doc.setFont('Helvetica', 'bold');
    doc.setFontSize(8.5);
    doc.setTextColor(51, 65, 85);
    doc.text(normalizePdfText(label), MARGIN_LEFT, currentY);
    doc.setFont('Helvetica', 'normal');
    for (const line of lines.length ? lines : ['-']) {
      doc.text(line, valueX, currentY);
      currentY += 4.25;
    }
  };

  const recordItem = (item: ActaPdfItem, index: number) => {
    const indent = 8;
    setBodyStyle();
    const descriptionLines = splitLines(item.descripcion, USABLE_W - indent);
    const metadata = [
      item.responsable ? `Responsable: ${item.responsable}` : '',
      item.estado ? `Estado: ${item.estado}` : '',
    ].filter(Boolean).join(' | ');
    doc.setFont('Helvetica', 'italic');
    doc.setFontSize(7.75);
    const metadataLines = splitLines(metadata, USABLE_W - indent);
    const estimatedHeight = descriptionLines.length * BODY_LINE_H
      + metadataLines.length * 3.75 + 3;

    if (estimatedHeight <= CONTENT_HEIGHT) ensureSpace(estimatedHeight);

    doc.setFont('Helvetica', 'bold');
    doc.setFontSize(9);
    doc.setTextColor(30, 64, 175);
    doc.text(`${index}.`, MARGIN_LEFT, currentY);

    setBodyStyle();
    writeWrappedLines(descriptionLines, MARGIN_LEFT + indent, BODY_LINE_H, false);

    if (metadataLines.length > 0) {
      doc.setFont('Helvetica', 'italic');
      doc.setFontSize(7.75);
      doc.setTextColor(100, 116, 139);
      writeWrappedLines(metadataLines, MARGIN_LEFT + indent, 3.75, false);
    }

    ensureSpace(3);
    currentY += 1;
    doc.setDrawColor(226, 232, 240);
    doc.line(MARGIN_LEFT + indent, currentY, MARGIN_LEFT + USABLE_W, currentY);
    currentY += 2;
  };

  /* Encabezado del documento */
  doc.setFont('Helvetica', 'bold');
  doc.setFontSize(16);
  doc.setTextColor(15, 23, 42);
  doc.text(`ACTA N° ${normalizePdfText(acta.codigo)}`, MARGIN_LEFT, currentY);
  currentY += 8;

  doc.setFont('Helvetica', 'normal');
  doc.setFontSize(8);
  doc.setTextColor(100, 116, 139);
  const meta = [acta.fecha, acta.hora, acta.lugar, acta.estado]
    .map(normalizePdfText)
    .filter(Boolean)
    .join(' | ');
  writeWrappedLines(splitLines(meta, USABLE_W), MARGIN_LEFT, 4);
  currentY += 4;

  sectionTitle('1. INFORMACIÓN');
  infoLine('Tipo:', acta.tipo);
  infoLine('Código:', acta.codigo);
  infoLine('Fecha:', acta.fecha);
  infoLine('Hora:', acta.hora);
  infoLine('Lugar:', acta.lugar);
  infoLine('Responsable:', acta.responsable);
  infoLine('Estado:', acta.estado);
  currentY += 3;

  sectionTitle('2. OBJETIVOS');
  bodyText(acta.objetivos);

  sectionTitle('3. TEMAS TRATADOS');
  if (acta.temas.length > 0) {
    acta.temas.forEach(topic => listItem(topic));
    currentY += 1;
  } else {
    mutedText('Sin temas tratados registrados.');
  }

  const { acuerdos, compromisos } = splitActaPdfItems(acta.acuerdosRel);

  sectionTitle('4. ACUERDOS');
  if (acuerdos.length > 0) {
    acuerdos.forEach((agreement, index) => recordItem(agreement, index + 1));
  } else {
    mutedText('Sin acuerdos registrados.');
  }

  sectionTitle('5. COMPROMISOS Y TAREAS');
  if (compromisos.length > 0) {
    compromisos.forEach((commitment, index) => recordItem(commitment, index + 1));
  } else {
    mutedText('Sin compromisos o tareas registrados.');
  }

  sectionTitle('6. INSTITUCIÓN PARTICIPANTES');
  if (acta.institucionParticipantes) {
    infoLine('Nombre de la institución:', acta.institucionParticipantes);
  } else {
    mutedText('Sin institución de participantes registrada.');
  }

  return doc;
}
