package service;

import dao.UserDao;
import annotation.Autowired;
import annotation.Service;

@Service
public class UserService {

	  @Autowired("userDao")
	  private UserDao userDao;

	  public void insert() {
	      userDao.insert();
	  }
}
