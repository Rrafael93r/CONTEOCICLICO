
import HeaderLeft from './HeaderLeft';
import HeaderRight from './HeaderRight';

const Header = () => {
  return (
    <div className="position-relative">
      <div className="navbar flex-md-nowrap p-3navbar-light bg-white shadow-sm">
        <div className="container-fluid px-4">
          <HeaderLeft />
          <HeaderRight />
        </div>
      </div>
    </div>
  );
};

export default Header;
