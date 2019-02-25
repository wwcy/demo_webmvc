package web;

import service.UserService;
import annotation.Autowired;
import annotation.Controller;
import annotation.RequestMapping;

@Controller
@RequestMapping("/user")
public class UserController {

	@Autowired("userService")
	private UserService userService;

	@RequestMapping("/insert")
	public void insert(){
		userService.insert();
	}
}
