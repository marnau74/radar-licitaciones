import { Routes } from '@angular/router';
import { guardaDeSesion } from './nucleo/guarda-sesion';

export const rutas: Routes = [
  {
    path: '',
    title: 'Buscar licitaciones · Radar de licitaciones',
    loadComponent: () => import('./busqueda/busqueda').then((m) => m.Busqueda),
  },
  {
    path: 'licitaciones/:id',
    title: 'Licitación · Radar de licitaciones',
    loadComponent: () => import('./detalle/detalle').then((m) => m.Detalle),
  },
  {
    path: 'cifras',
    title: 'Cifras · Radar de licitaciones',
    loadComponent: () => import('./cifras/cifras').then((m) => m.Cifras),
  },
  {
    path: 'alertas',
    title: 'Mis alertas · Radar de licitaciones',
    canActivate: [guardaDeSesion],
    loadComponent: () => import('./alertas/alertas').then((m) => m.Alertas),
  },
  {
    path: 'alertas/nueva',
    title: 'Nueva alerta · Radar de licitaciones',
    canActivate: [guardaDeSesion],
    loadComponent: () => import('./alertas/formulario-alerta').then((m) => m.FormularioAlerta),
  },
  {
    path: 'alertas/:id',
    title: 'Editar alerta · Radar de licitaciones',
    canActivate: [guardaDeSesion],
    loadComponent: () => import('./alertas/formulario-alerta').then((m) => m.FormularioAlerta),
  },
  {
    path: 'avisos',
    title: 'Mis avisos · Radar de licitaciones',
    canActivate: [guardaDeSesion],
    loadComponent: () => import('./avisos/avisos').then((m) => m.Avisos),
  },
  {
    path: 'datos',
    title: 'Los datos · Radar de licitaciones',
    loadComponent: () => import('./datos/datos').then((m) => m.Datos),
  },
  {
    path: '**',
    title: 'Página no encontrada · Radar de licitaciones',
    loadComponent: () => import('./no-encontrada').then((m) => m.NoEncontrada),
  },
];
