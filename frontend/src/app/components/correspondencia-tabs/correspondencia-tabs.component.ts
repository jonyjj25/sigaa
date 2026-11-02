import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IonIcon } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { mailOutline, readerOutline } from 'ionicons/icons';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-correspondencia-tabs',
  standalone: true,
  imports: [RouterLink, IonIcon],
  templateUrl: './correspondencia-tabs.component.html',
  styleUrls: ['./correspondencia-tabs.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CorrespondenciaTabsComponent {
  protected readonly auth = inject(AuthService);
  readonly active = input.required<'notas' | 'memos'>();
  constructor() { addIcons({ mailOutline, readerOutline }); }
}
