import {Component, Input, OnInit} from '@angular/core';
import {SecurityService} from '../../security/security.service';
import {DomainService} from '../../security/domain.service';
import {Domain} from '../../security/domain';
import {MdDialog} from '@angular/material';
import {CancelDialogComponent} from '../cancel-dialog/cancel-dialog.component';
import {AlertService} from '../alert/alert.service';
import {ActivatedRoute, ActivatedRouteSnapshot, Router, RoutesRecognized} from '@angular/router';

@Component({
  selector: 'domain-selector',
  templateUrl: './domain-selector.component.html',
  styleUrls: ['./domain-selector.component.css']
})
export class DomainSelectorComponent implements OnInit {

  showDomains: boolean;
  displayDomains: boolean;
  currentDomainCode: string;
  domainCode: string;
  domains: Domain[];

  @Input()
  currentComponent: any;

  constructor(private domainService: DomainService,
              private securityService: SecurityService,
              private dialog: MdDialog,
              private alertService: AlertService,
              private router: Router,
              private route: ActivatedRoute) {
  }

  async ngOnInit() {
    try {
      const isMultiDomain = await this.domainService.isMultiDomain().toPromise();

      if (isMultiDomain && this.securityService.isCurrentUserSuperAdmin()) {
        this.domains = await this.domainService.getDomains();
        this.displayDomains = true;
        this.showDomains = this.shouldShowDomains(this.route.snapshot);

        this.domainService.getCurrentDomain().subscribe(domain => {
          this.domainCode = this.currentDomainCode = (domain ? domain.code : null);
        });
      }

      this.router.events.subscribe(event => {
        if (event instanceof RoutesRecognized) {
          let route = event.state.root.firstChild;
          this.showDomains = this.shouldShowDomains(route);
        }
      });
    } catch (error) {
      console.log('error while calling backend for getting domains information: ' + error);
    }
  }

  private shouldShowDomains(route: ActivatedRouteSnapshot) {
    return this.displayDomains && !route.data.isDomainIndependent;
  }

  async changeDomain() {
    let canChangeDomain = Promise.resolve(true);
    if (this.supportsDirtyOperations() && this.currentComponent.isDirty()) {
      canChangeDomain = this.dialog.open(CancelDialogComponent).afterClosed().toPromise<boolean>();
    }

    try {
      const canChange = await canChangeDomain;
      if (!canChange) throw false;

      if (this.currentComponent.beforeDomainChange) {
        try {
          this.currentComponent.beforeDomainChange();
        } catch (e) {
          console.log(e);
        }
      }

      const domain = this.domains.find(d => d.code == this.domainCode);
      await this.domainService.setCurrentDomain(domain);

      this.alertService.clearAlert();

      this.domainService.setAppTitle();

      if (this.currentComponent.ngOnInit) {
        try {
          this.currentComponent.ngOnInit();
        } catch (e) {
          console.log(e);
        }
      }

    } catch (ex) { // domain not changed -> reset the combo value
      this.domainCode = this.currentDomainCode;
      if (ex.status <= 0) {
        this.alertService.exception('The server didn\'t respond, please try again later', ex);
      }
    }
  }

  private supportsDirtyOperations() {
    return this.currentComponent && this.currentComponent.isDirty
      && this.currentComponent.isDirty instanceof Function;
  }
}

