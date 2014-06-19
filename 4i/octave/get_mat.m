function A = get_mat(h,w)
  %4 directions
  %all
  all = reshape([1:h*w],w,h);
  all = all';
  %left,right,-1
  tmp = all(:,1:end-1)(:)';
  x = tmp;
  y = tmp + 1;
  z = repmat(-1,size(tmp));
  %right,left,-1
  tmp = all(:,2:end)(:)';
  x = [x,tmp];
  y = [y,tmp - 1];
  z = [z,repmat(-1,size(tmp))];
  %up,down,-1
  tmp = all(1:end-1,:)(:)';
  x = [x,tmp];
  y = [y,tmp + w];
  z = [z,repmat(-1,size(tmp))];
  %down,up,-1
  tmp = all(2:end,:)(:)';
  x = [x,tmp];
  y = [y,tmp - w];
  z = [z,repmat(-1,size(tmp))];
  %self
  number = 4.*ones(h,w);
  number(1,:) -= 1;
  number(:,1) -= 1;
  number(h,:) -= 1;
  number(:,w) -= 1;
  x = [x,[1:h*w]];
  y = [y,[1:h*w]];
  z = [z,number'(:)'];
  %then ...
  A = sparse(x,y,z);
end