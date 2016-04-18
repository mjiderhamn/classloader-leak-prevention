package se.jiderhamn.classloader.leak.prevention.preinit;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.GregorianCalendar;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;

import org.junit.Before;
import org.junit.Ignore;

/**
 * Test cases for {@link DatatypeConverterImplInitiator}
 * @author Mattias Jiderhamn
 */
@Ignore // Doesn't leak in Java 1.7.0 (_55, _56), but does in 1.8.0 (_74) 
public class DatatypeConverterImplInitiatorTest extends PreClassLoaderInitiatorTestBase<DatatypeConverterImplInitiator> {
  @Before
  public void setSystemProperty() {
    System.setProperty("javax.xml.datatype.DatatypeFactory", MyDatatypeFactory.class.getName());
  }

  /** Custom {@link DatatypeFactory} loaded by "our" classloader */
  public static class MyDatatypeFactory extends DatatypeFactory {
    @Override
    public Duration newDuration(String lexicalRepresentation) {
      throw new UnsupportedOperationException();
    }
  
    @Override
    public Duration newDuration(long durationInMilliSeconds) {
      throw new UnsupportedOperationException();
    }
  
    @Override
    public Duration newDuration(boolean isPositive, BigInteger years, BigInteger months, BigInteger days, BigInteger hours, BigInteger minutes, BigDecimal seconds) {
      throw new UnsupportedOperationException();
    }
  
    @Override
    public XMLGregorianCalendar newXMLGregorianCalendar() {
      throw new UnsupportedOperationException();
    }
  
    @Override
    public XMLGregorianCalendar newXMLGregorianCalendar(String lexicalRepresentation) {
      throw new UnsupportedOperationException();
    }
  
    @Override
    public XMLGregorianCalendar newXMLGregorianCalendar(GregorianCalendar cal) {
      throw new UnsupportedOperationException();
    }
  
    @Override
    public XMLGregorianCalendar newXMLGregorianCalendar(BigInteger year, int month, int day, int hour, int minute, int second, BigDecimal fractionalSecond, int timezone) {
      throw new UnsupportedOperationException();
    }
  }
}