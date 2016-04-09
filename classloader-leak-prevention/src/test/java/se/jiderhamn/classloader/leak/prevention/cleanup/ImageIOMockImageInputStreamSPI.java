package se.jiderhamn.classloader.leak.prevention.cleanup;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import javax.imageio.spi.ImageInputStreamSpi;
import javax.imageio.stream.ImageInputStream;

public class ImageIOMockImageInputStreamSPI extends ImageInputStreamSpi {

    @Override
    public ImageInputStream createInputStreamInstance(Object input, boolean useCache, File cacheDir) throws IOException {
        throw new IllegalArgumentException("mock class");
    }

    @Override
    public String getDescription(Locale locale) {
        return "mock ImageInputStream provider";
    }

}
