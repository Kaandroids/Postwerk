import { Type } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

/**
 * Spins up a TestBed with the real {@link HttpClient} backed by {@link HttpTestingController},
 * for the handful of services that call {@link HttpClient} directly (auth, session, wizard, and
 * the {@code ApiService} wrapper itself) rather than going through the mockable {@code ApiService}.
 *
 * @returns the resolved service instance and the controller for asserting/flushing requests.
 * @example
 *   const { service, httpMock } = setupHttpService(AuthService);
 *   service.login('a@b.c', 'pw').subscribe();
 *   httpMock.expectOne(r => r.url.endsWith('/auth/login')).flush({ token: 't' });
 *   httpMock.verify();
 */
export function setupHttpService<T>(service: Type<T>, extraProviders: unknown[] = []) {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting(), ...extraProviders],
  });
  return {
    service: TestBed.inject(service),
    httpMock: TestBed.inject(HttpTestingController),
  };
}
