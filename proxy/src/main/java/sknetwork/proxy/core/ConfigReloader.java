package sknetwork.proxy.core;

import java.io.IOException;

@FunctionalInterface
public interface ConfigReloader {

	ProxySettings reread() throws IOException;
}
