import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import annotation.Autowired;
import annotation.Controller;
import annotation.Repository;
import annotation.RequestMapping;
import annotation.Service;

@SuppressWarnings("serial")
@WebServlet(urlPatterns="/*",loadOnStartup=0,initParams={@WebInitParam(name="base-package",value="com.it")})
public class DispatchServlet extends HttpServlet{

	private static final String EMPTY = "";

	//扫描的基础包
	private String basePackage = EMPTY;
	//扫描的基础包下面类的全类名
	private List<String> packagesName = new ArrayList<String>();
	//key:注解里的值（默认为类名第一个字母小写） value：类的实例化对象
	private Map<String,Object> instanceMap = new HashMap<String, Object>();
	//key:类的全类名 	value：注解里的值（默认为类名第一个字母小写）
	private Map<String,String> nameMap = new HashMap<String, String>();
	//key:请求的路径	value:所调用的方法
	private Map<String,Method> urlMethodMap = new HashMap<String, Method>();
	//key:controller里的方法实例	value：当前类的全类名
	private Map<Method,String> methodPackageMap = new HashMap<Method, String>();
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		basePackage = config.getInitParameter("base-package");
		scanBasePackage(basePackage);
		instance(packagesName);
		ioc();
		handlerUrlMethod();
	}
	
	@SuppressWarnings("unused")
	private void scanBasePackage(String basePackage){
		Optional<URL> resource = Optional.ofNullable(this.getClass().getClassLoader().getResource(basePackage.replaceAll("\\*", "/")));
		String path = resource.map(URL::getPath).orElse(EMPTY);
		File basePackageFile = new File(path);
		Optional<File[]> files = Optional.ofNullable(basePackageFile.listFiles());
		files.ifPresent(file ->{
			for(File temp : file){
				if(temp.isDirectory()){
					scanBasePackage(basePackage+"."+temp.getName());
				}
				if(temp.isFile()){
					packagesName.add(basePackage+"."+temp.getName().split("\\.")[0]);
				}
			}
			
		});
	}
	
	@SuppressWarnings({ "unused" })
	private void instance(List<String> packagesNames){
		if(packagesNames.isEmpty()){
			return;
		}
		
		packagesNames.forEach(packageName ->{
			try {
				
				Class<?> clazz = Class.forName(packageName);
				String subName = clazz.getName().substring(clazz.getName().lastIndexOf(".")).replaceAll("\\.", EMPTY);
				String clazzName = subName.substring(0, 1).toLowerCase() + subName.substring(1);
				if(clazz.isAnnotationPresent(Controller.class)){
					Controller controller = (Controller)clazz.getAnnotation(Controller.class);
					String controllerName = controller.value();
					if(EMPTY.equals(controllerName)){
						controllerName = clazzName;
					}
					instanceMap.put(controllerName, clazz.newInstance());
					nameMap.put(packageName, controllerName);
				}
				
				if(clazz.isAnnotationPresent(Service.class)){
					Service service = (Service)clazz.getAnnotation(Service.class);
					String serviceName = service.value();
					if(!EMPTY.equals(serviceName)){
						serviceName = clazzName;
					}
					instanceMap.put(serviceName, clazz.newInstance());
					nameMap.put(packageName, serviceName);
				}
				
				if(clazz.isAnnotationPresent(Repository.class)){
					Repository repository = (Repository)clazz.getAnnotation(Repository.class);
					String repositoryName = repository.value();
					if(!EMPTY.equals(repositoryName)){
						repositoryName = clazzName;
					}
					instanceMap.put(repositoryName, clazz.newInstance());
					nameMap.put(packageName, repositoryName);
				}	
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}
	
	@SuppressWarnings("unused")
	private void ioc(){
		for(Map.Entry<String, Object> instance : instanceMap.entrySet()){
			Field[] fields = instance.getValue().getClass().getDeclaredFields();
			for(Field field : fields){
				if(field.isAnnotationPresent(Autowired.class)){
					String value = field.getAnnotation(Autowired.class).value();
					field.setAccessible(true);
					try {
						field.set(instance.getValue(), instanceMap.get(value));
					} catch (IllegalArgumentException e) {
						e.printStackTrace();
					} catch (IllegalAccessException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	@SuppressWarnings({ "unused", "unchecked", "rawtypes" })
	private void handlerUrlMethod(){
		if(packagesName.isEmpty()){
			return;
		}
		packagesName.forEach(packaeName ->{
			try {
				Class clazz = Class.forName(packaeName);
				if(clazz.isAnnotationPresent(Controller.class)){
					Method[] methods = clazz.getMethods();
					StringBuffer baseUrl = new StringBuffer();
					if(clazz.isAnnotationPresent(RequestMapping.class)){
						RequestMapping controllerClazzMapping = (RequestMapping)clazz.getAnnotation(RequestMapping.class);
						baseUrl.append(controllerClazzMapping.value());
					}
					for(Method method : methods){
						if(method.isAnnotationPresent(RequestMapping.class)){
							baseUrl.append(method.getAnnotation(RequestMapping.class).value());
							urlMethodMap.put(baseUrl.toString(), method);
							methodPackageMap.put(method, packaeName);
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		doPost(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		String uri = req.getRequestURI();
		String contextPath = req.getContextPath();
		String path = uri.replace(contextPath, EMPTY);
		Optional<Method> method = Optional.ofNullable(urlMethodMap.get(path));
		method.ifPresent(m ->{
			String packageName = methodPackageMap.get(m);
			String controllerName = nameMap.get(packagesName);
			Object controllerObject = instanceMap.get(controllerName);
			m.setAccessible(true);
			try {
				m.invoke(controllerObject);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
	}

	@Override
	public void destroy() {
		super.destroy();
	}

}
