#include <unistd.h>
#include <sys/types.h>
#include <fcntl.h>
#include <string.h>

int main()
{
	int w = 100;
	printf("Before the value is %d\n",w);
	void * p = &w;
	char buf[50];
	sprintf(buf,"writeval %p %d",p,480);
	int fd = open("/proc/hello_proc",O_RDWR);
	write(fd,buf,strlen(buf));
	close(fd);
	printf("After the value is %d\n",w);
	return ;
}
