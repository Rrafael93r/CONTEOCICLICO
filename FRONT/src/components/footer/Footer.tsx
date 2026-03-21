const Footer: React.FC = () => {
  return (
    <footer className="footer border-top p-3" style={{ marginLeft: "var(--sidebar-width)" }}>
      <div className="text-center d-flex flex-column align-items-center justify-content-center">
        <span className="text-muted">Copyright © Todos los derechos reservados</span>
        <span
          className="text-muted mt-1"
          style={{ fontSize: '0.75rem', cursor: 'default', opacity: 0.7 }}
          title="Rafael Rojas Ramírez"
        >
          Desarrollado por R.R.R
        </span>
      </div>
    </footer>
  );
};

export default Footer;