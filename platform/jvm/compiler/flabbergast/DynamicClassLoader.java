package flabbergast;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public final class DynamicClassLoader extends ClassLoader implements BiConsumer<String, byte[]> {
  private final Map<String, byte[]> byteCode = new HashMap<>();

  @Override
  public void accept(String fileName, byte[] contents) {
    byteCode.put(fileName.replace('/', '.'), contents);
  }

  @Override
  protected Class<?> findClass(String className) throws ClassNotFoundException {
    byte[] code = byteCode.get(className);
    if (code == null) {
      throw new ClassNotFoundException(className + " not found");
    }
    return defineClass(className, code, 0, code.length);
  }
}
