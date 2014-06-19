#include <linux/module.h>
#include <linux/proc_fs.h>
#include <linux/seq_file.h>
#include <linux/types.h>
#include <linux/uaccess.h>

#define BUFFER_MAX 100
static char buffer[BUFFER_MAX];

static ssize_t hello_write(struct file* f,char __user *buf,size_t s,loff_t* l)
{
	memset(buffer,0,sizeof(buffer));
	int max = sizeof(buffer) - 1;
	if(copy_from_user(buffer,buf,s>max ? max : s))
		return -EFAULT;	
	return s;
}

static int hello_proc_show(struct seq_file *m, void *v) {
  	seq_printf(m, "Hello proc! The string is %s...\n",buffer);
  	return 0;
}

static int hello_proc_open(struct inode *inode, struct  file *file) {
  return single_open(file, hello_proc_show, NULL);
}

static const struct file_operations hello_proc_fops = {
  .owner = THIS_MODULE,
  .open = hello_proc_open,
  .read = seq_read,
  .write = hello_write,
  .llseek = seq_lseek,
  .release = single_release,
};

static int __init hello_proc_init(void) {
  proc_create("hello_proc", 0777, NULL, &hello_proc_fops);
  return 0;
}

static void __exit hello_proc_exit(void) {
  remove_proc_entry("hello_proc", NULL);
}

MODULE_LICENSE("GPL");
module_init(hello_proc_init);
module_exit(hello_proc_exit); 
