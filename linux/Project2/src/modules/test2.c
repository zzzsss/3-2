#include <linux/init.h>
#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/moduleparam.h>

static char *who = "No-one";
static int number ;
static int  list[] = {1,2,3};

static int the_number = 0;

module_param(who,charp,0664);
module_param(number,int,0664);
module_param_array(list,int,&the_number,0664);

static int __init hello_init(void)
{
	printk(KERN_INFO"Greeting from a new module,name is %s,number is %d.\n",who,number);
	printk("List's length is %d,and they are ",the_number);
	int i=0;
	for(i=0;i<the_number;i++)
		printk("%d,",list[i]);
	printk("\n");
	return 0;
}

static void __exit hello_exit(void)
{
	printk(KERN_INFO"2Bye...\n");
}

module_init(hello_init);
module_exit(hello_exit);

MODULE_LICENSE("GPL");
