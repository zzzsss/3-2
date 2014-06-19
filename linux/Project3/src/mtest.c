#include <linux/module.h>
#include <linux/proc_fs.h>
#include <linux/seq_file.h>
#include <linux/types.h>
#include <linux/uaccess.h>
#include <linux/ctype.h>
#include <linux/string.h>

#include <asm/pgtable.h>
#include <linux/mm.h>
#include <linux/highmem.h>

#define _DEBUG 1


#define BUFFER_MAX 100
static char buffer[BUFFER_MAX+1];	//plus for extra safety when parsing
void dealing_it(char*,int);

static ssize_t hello_write(struct file* f,char __user *buf,size_t s,loff_t* l)
{
	memset(buffer,0,sizeof(buffer));
	int max = sizeof(buffer) - 1;
	int size = (s>max) ? max : s;
	if(copy_from_user(buffer,buf,size))
		return -EFAULT;	
	buffer[size] = '\0';
	dealing_it(buffer,size);
	return s;
}

static int hello_proc_show(struct seq_file *m, void *v) {
  	seq_printf(m, "Nothing...\n");
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

MODULE_LICENSE("GPL");
static int __init hello_proc_init(void) {
  proc_create("hello_proc", 0777, NULL, &hello_proc_fops);
  return 0;
}

static void __exit hello_proc_exit(void) {
  remove_proc_entry("hello_proc", NULL);
}

module_init(hello_proc_init);
module_exit(hello_proc_exit); 

/* --- helper functions --- */

//convert number--- return 0 if ok,-1 error
//In hex or dec..
static int convert_number(const char *str,long *result)
{
	int base = 10;
	int len = strlen(str);
	//decide on base
	if(len > 2){
		if(str[0]=='0' && (str[1]=='x'||str[1]=='X')){
			base = 16;
			str += 2;
		}
	}
	unsigned long number = 0;
	switch(base){
		case 10:
			while(1){
				if(*str == '\0')
					break;
				else if(*str >= '0' && *str <= '9')
					number = number * base + *str - '0';
				else
					return -1;
				str++;
			}
			break;
		case 16:
			while(1){
				if(*str == '\0')
					break;
				else if(*str >= '0' && *str <= '9')
					number = number * base + (*str - '0');
				else if(*str >= 'a' && *str <= 'f')
					number = number * base + (*str - 'a') + 10;
				else if(*str >= 'A' && *str <= 'F')
					number = number * base + (*str - 'A') + 10;
				else
					return -1;
				str++;
			}
			break;
		default:
			break;
	}
	*result = number;
	return 0;
}

//the dealing function
typedef void (*CMD_HANDLE) (int,char**);
#define M_DECLARE_HANDLE(name)	\
	void name(int,char**)
M_DECLARE_HANDLE(m_list);
M_DECLARE_HANDLE(m_find);
M_DECLARE_HANDLE(m_write);

#define CMD_TOTAL  3
const char* CMDs[CMD_TOTAL] = {"listvma","findpage","writeval"};
CMD_HANDLE CMD_TODO[CMD_TOTAL] = {m_list,m_find,m_write};
#define MAX_PARAM 10

void dealing_it(char * buf,int size)
{
	int argc = 0;
	char * argv[MAX_PARAM];
	int i=0;
	//parse the string --- maybe out of size by 1
	while(argc < MAX_PARAM){
		/* skip blank */
		for(;i<size && isspace(buf[i]);i++);
		//empty line
		if(i>=size)
			break;
		argv[argc++] = &buf[i];
		for(;i<size && !isspace(buf[i]);i++);
		//set \0
		buf[i++] = '\0';
	}
#ifdef _DEBUG
	printk(KERN_INFO"String is %s; the argc is %d; argv is ",buf,argc);
	for(i=0;i<argc;i++)
		printk("%s;",argv[i]);
	printk("\n");
#endif
	//then deal it
	if(argc == 0)//nothing
		return;
	for(i=0;i<CMD_TOTAL;i++){
		if(strcmp(CMDs[i],argv[0])==0){
			(CMD_TODO[i])(argc,argv);
			return;
		}
	}
	printk(KERN_INFO"Unkown cmd %s\n",argv[0]);
	return;
}

void m_list(int argc,char **argv)
{
	//just don't check args...
	struct mm_struct *mm = current->mm;
	if(!mm){
		printk(KERN_INFO"No mm,can't list vma\n");
		return;
	}
	struct vm_area_struct *vma = mm->mmap;
	printk(KERN_INFO"The vma are:\n");
	for(;vma;vma=vma->vm_next){
		unsigned long start,end,flags;
		start = vma->vm_start;
		end = vma->vm_end;
		flags = vma->vm_flags;
		printk(KERN_INFO"0x%08lx-0x%08lx %c%c%c\n",start,end,
				flags & VM_READ ? 'r' : '-',
				flags & VM_WRITE ? 'w' : '-',
				flags & VM_EXEC ? 'x' : '-'
				);
	}
}
void m_find(int argc,char **argv)
{
	if(argc != 2){
		printk(KERN_INFO"Wrong param number for findpage (should be 1)...\n");
		return;
	}
	unsigned long addr = 0;
	if(convert_number(argv[1],&addr)<0){
		printk(KERN_INFO"Illegal findpage addr number %s\n",argv[1]);
		return;
	}
#ifdef _DEBUG
	printk(KERN_INFO"Command ok: findpage %p\n",addr);
#endif
	/* walk through the page table */
	pgd_t *pgd;
	pud_t *pud;
	pmd_t *pmd;
	pte_t *pte;
	pte_t p;
	pgd = pgd_offset(current->mm,addr);
	if ( !pgd_none(*pgd) && !pgd_bad(*pgd) ){
		pud = pud_offset(pgd,addr);
		if ( !pud_none(*pud) && !pud_bad(*pud) ){
			pmd = pmd_offset(pud,addr);
			if ( !pmd_none(*pmd) && !pmd_bad(*pmd) ){
				pte = pte_offset_map(pmd,addr);
				if(pte){
					p = *pte;
					pte_unmap(pte);
					void * pa = pte_pfn(p) << PAGE_SHIFT;
#ifdef _DEBUG
					printk(KERN_INFO"Page table entry is %lu\n",pte_val(p));
#endif
					printk(KERN_INFO"Translation for address 0x%p is 0x%p with offset %x\n",addr,pa,addr&(PAGE_SIZE-1));
					return;
				}
			}
		}
	}
	/* nope */
	printk(KERN_INFO"Translation not find for address 0x%p\n",addr);
}

void m_write(int argc,char **argv)
{
	if(argc != 3){
		printk(KERN_INFO"Wrong param number for writeval (should be 2)...\n");
		return;
	}
	unsigned long addr = 0,val = 0;
	if(convert_number(argv[1],&addr)<0){
		printk(KERN_INFO"Illegal writeval addr number %s\n",argv[1]);
		return;
	}
	if(convert_number(argv[2],&val)<0){
		printk(KERN_INFO"Illegal writeval val number %s\n",argv[2]);
		return;
	}
#ifdef _DEBUG
	printk(KERN_INFO"Command ok: writeval %p %lu\n",addr,val);
#endif

	//write the virtual address
	int len = sizeof(unsigned long);
	int ret = 0;
	//int ret = access_process_vm(current,addr,&val,len,1);
	/*
	struct vm_area_struct *m = find_vma(current->mm,addr);
	if(m)
		ret = generic_access_phys(current->mm->mmap,addr,&val,len,1);
	*/
	ret = copy_to_user(addr,&val,len);
	/*
	if(ret == len)
		printk(KERN_INFO"Write ok\n");
	else
		printk(KERN_INFO"Write err, return %d\n",ret);
	*/
}
