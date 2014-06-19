% rgb to gray of matlab script %

function what = matlab_rgb(Filename,show)
  im = im2double(imread(Filename));
  [n,m,ch] = size(im);
  if(ch==1)
    im = repmat(im,[1,1,3]);
  end
  if(show)
	figure;
	imshow(im);
  end
  tic;
  out = rgb2gray(im);
  toc;
  a = min(out(:));
  b = max(out(:));
  out = (out-a)/(b-a);
  if(show)
	figure;
	imshow(out);
  end
  what = out;
end

function out = rgb2gray(img_in)
  [height,width,ch] = size(img_in);
  A = get_mat(height,width);
  val = get_val(img_in,height,width);
  val = val';
  what = val(:);
  out = reshape(A\what,width,height);
  out = out';
end

function val = get_val(im,h,w)
  %parameters
  lambda = 0.8;
  M_num = 30;
  b = 3;
  %calculate
  Q = calc_landmark(im,b,M_num);
  %% first the ij-neigbour
  %four directions
  d1 = [im(:,1:end-1,:) - im(:,2:end,:),zeros(h,1,3)];
  d2 = [zeros(h,1,3),im(:,2:end,:) - im(:,1:end-1,:)];
  d3 = [zeros(1,w,3);im(2:end,:,:) - im(1:end-1,:,:)];
  d4 = [im(1:end-1,:,:) - im(2:end,:,:);zeros(1,w,3)];
  val = (1-lambda).*(calc_delta(d1) + calc_delta(d2) + calc_delta(d3) + calc_delta(d4));
  %for the Q --- global
  number = 4.*ones(h,w);
  number(1,:) = number(1,:) - 1;
  number(:,1) = number(:,1) - 1;
  number(h,:) = number(h,:) - 1;
  number(:,w) = number(:,w) - 1;
  for i=1:M_num
    color = im(Q(i,1),Q(i,2),:);
    %i-k
    i_k = im - repmat(color,h,w);
    i_k_value = calc_delta(i_k);
    val = val + number*(lambda/2).*i_k_value;
    %k-j
    i_k_value = -1 .* i_k_value;
    qd1 = [i_k_value(:,2:end,:),zeros(h,1)];
    qd2 = [zeros(h,1),i_k_value(:,1:end-1,:)];
    qd3 = [zeros(1,w);i_k_value(1:end-1,:,:)];
    qd4 = [i_k_value(2:end,:,:);zeros(1,w)];
    val = val + (lambda/2).*(qd1+qd2+qd3+qd4);
  end
  val = val / ((1-lambda)+lambda*M_num/2);
end

function d = calc_delta(diff)
  tmp = sum(diff.^3,3)/3;
  d = (0-(tmp<0)+(tmp>=0)).*abs(tmp).^(1/3);
end

%get mat
function A = get_mat(h,w)
  %4 directions
  %all
  all = reshape([1:h*w],w,h);
  all = all';
  %left,right,-1
  tmp = all(:,1:end-1);
  tmp = tmp(:)';
  x = tmp;
  y = tmp + 1;
  z = repmat(-1,size(tmp));
  %right,left,-1
  tmp = all(:,2:end);
  tmp = tmp(:)';
  x = [x,tmp];
  y = [y,tmp - 1];
  z = [z,repmat(-1,size(tmp))];
  %up,down,-1
  tmp = all(1:end-1,:);
  tmp = tmp(:)';
  x = [x,tmp];
  y = [y,tmp + w];
  z = [z,repmat(-1,size(tmp))];
  %down,up,-1
  tmp = all(2:end,:);
  tmp = tmp(:)';
  x = [x,tmp];
  y = [y,tmp - w];
  z = [z,repmat(-1,size(tmp))];
  %self
  number = 4.*ones(h,w);
  number(1,:) = number(1,:) - 1;
  number(:,1) = number(:,1) - 1;
  number(h,:) = number(h,:) - 1;
  number(:,w) = number(:,w) - 1;
  x = [x,[1:h*w]];
  y = [y,[1:h*w]];
  number = number';
  what = number(:);
  what = what';
  z = [z,what];
  %then ...
  A = sparse(x,y,z);
end

%calc_landmark
function q = calc_landmark(im,b,M_num)
  [h,w,ch] = size(im);
  tmp = uint16(bitshift(im2uint8(im),b-8));
  index = bitshift(tmp(:,:,1),2*b)+bitshift(tmp(:,:,2),b)+tmp(:,:,3);
  rep_index = repmat(index,[1,1,3]);
  
  %select the popular index
  all_index = sort(index(:));
  all_index_unique = unique(all_index);
  all_index_num = cat(2,all_index_unique,histc(all_index,unique(all_index)));
  size_tmp = size(all_index_num);
  while(size_tmp(1) < M_num)
    all_index_num = cat(1,all_index_num,all_index_num);
    size_tmp = size(all_index_num);
  end
  select_index = sortrows(all_index_num,2);
  select_index = select_index(end-M_num+1:end,1);
  %select the popular avg color
  select_color = zeros(M_num,3);
  for k=1:M_num
    curr_index = rep_index == select_index(k);
    num = sum(curr_index(:)) / 3;
    tmp = sum(sum(im.*curr_index,1),2);
    tmp = tmp(:);
    select_color(k,:) = tmp' ./ num ;
  end
  
  %calc result
  for k=1:M_num
    color = select_color(k,:);
    color_diff = cat(3,im(:,:,1)-color(1),im(:,:,2)-color(2),im(:,:,3)-color(3));
    value = sum((color_diff).^2,3);
    value = value';
    [the_min,place] = min(value(:));
    place = uint32(place)-1;
    q(k,:) = [(place-mod(place,w))/w+1,mod(place,w)+1];
  end
end

