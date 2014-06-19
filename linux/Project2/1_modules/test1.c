#include <linux/init.h>
#include <linux/module.h>
#include <linux/kernel.h>

static int __init hello_init(void)
{
	printk("\0012Greeting from a new module...\n");
	return 0;
}

static void __exit hello_exit(void)
{
	printk("\0012Bye...\n");
}

module_init(hello_init);
module_exit(hello_exit);

MODULE_LICENSE("GPL");
