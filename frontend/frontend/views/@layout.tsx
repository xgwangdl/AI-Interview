import { createMenuItems, useViewConfig } from '@vaadin/hilla-file-router/runtime.js';
import { effect, signal } from '@vaadin/hilla-react-signals';
import { AppLayout, DrawerToggle, Icon, SideNav, SideNavItem } from '@vaadin/react-components';
import { Suspense, useEffect } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';

const documentTitleSignal = signal('');
effect(() => {
  document.title = documentTitleSignal.value;
});

// Publish for Vaadin to use
(window as any).Vaadin.documentTitleSignal = documentTitleSignal;

export default function MainLayout() {
  const currentTitle = useViewConfig()?.title;
  const navigate = useNavigate();
  const location = useLocation();

  const isAdmin = localStorage.getItem('isAdmin') === 'true';

  useEffect(() => {
    if (currentTitle) {
      documentTitleSignal.value = currentTitle;
    }
  }, [currentTitle]);

  return (
    <AppLayout primarySection="drawer">
      <div slot="drawer" className="flex flex-col justify-between h-full p-m">
        <header className="flex flex-col gap-m">
          <span className="font-semibold text-l">拥抱AI</span>

          <SideNav onNavigate={({ path }) => navigate(path!)} location={location}>
            {createMenuItems().filter(item => ((item.order == 2 || item.order == 3) && isAdmin)).map(({ to, title, icon }) => (
              <SideNavItem path={to} key={to}>
                {icon ? <Icon icon={icon} slot="prefix"></Icon> : <></>}
                {title}
              </SideNavItem>
            ))}
            {createMenuItems().filter(item => item.order != 2 && item.order != 3 && !isAdmin).map(({ to, title, icon }) => (
              <SideNavItem path={to} key={to}>
                {icon ? <Icon icon={icon} slot="prefix"></Icon> : <></>}
                {title}
              </SideNavItem>
            ))}
          </SideNav>
        </header>
      </div>

      <DrawerToggle slot="navbar" aria-label="Menu toggle"></DrawerToggle>
      <h1 slot="navbar" className="text-l m-0">
        {documentTitleSignal}
      </h1>

      <Suspense>
        <Outlet />
      </Suspense>
    </AppLayout>
  );
}
