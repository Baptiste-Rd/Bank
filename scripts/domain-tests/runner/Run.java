package runner;
import java.lang.reflect.*; import java.nio.file.*; import java.util.*;
public class Run { public static void main(String[] a) throws Exception {
  int ok=0, ko=0; Path root=Paths.get(a[0]);
  List<Path> files=new ArrayList<>(); Files.walk(root).filter(p->p.toString().endsWith("Test.class")).forEach(files::add);
  for(Path p: files){ String cn=root.relativize(p).toString().replace('/', '.').replace(".class","");
    Class<?> c=Class.forName(cn);
    for(Method m: c.getDeclaredMethods()){ if(!m.isAnnotationPresent(org.junit.Test.class)) continue;
      Object inst=c.getDeclaredConstructor().newInstance();
      try{ m.invoke(inst); ok++; }catch(InvocationTargetException e){ ko++; System.out.println("FAIL "+cn+" > "+m.getName()+" : "+e.getCause()); }
    }}
  System.out.println(ok+" passed, "+ko+" failed"); System.exit(ko==0?0:1);
}}
