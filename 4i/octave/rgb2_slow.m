% rgb to gray %
source calc_landmark2.m;
source get_mat.m;

function what = main(Filename)
  rgb = (imread(Filename));
  %cform = makecform('srgb2lab');
  %im = applycform(rgb,cform);
  im = im2double(rgb);
  %for debug
  [n,m,ch] = size(im);
  if(ch==1)
    im = repmat(im,[1,1,3]);
  end
  figure;
  imshow(im);
  %tic;
  out = rgb2gray(im);
  %toc;
  a = min(out(:));
  b = max(out(:));
  out = (out-a)/(b-a);
  figure;
  imshow(out);
end

function out = rgb2gray(img_in)
  [height,width,ch] = size(img_in);
  S = get_mat(height,width);
  tic;
  linear = get_val(img_in,height,width,S);
  toc;
  out = reshape(linear,width,height);
  out = out';
end

function linear = get_val(im,h,w,S)
  %parameters
  lambda = 0.2;
  M_num = 30;
  b = 3;
  %calculate right side
  Q = calc_landmark(im,b,M_num);
  %% first the ij-neigbour
  %four directions
  d1 = [im(:,1:end-1,:) - im(:,2:end,:),zeros(h,1,3)];
  d2 = [zeros(h,1,3),im(:,2:end,:) - im(:,1:end-1,:)];
  d3 = [zeros(1,w,3);im(2:end,:,:) - im(1:end-1,:,:)];
  d4 = [im(1:end-1,:,:) - im(2:end,:,:);zeros(1,w,3)];
  val = (1-lambda).*(calc_delta(d1) + calc_delta(d2) + calc_delta(d3) + calc_delta(d4));
  %for the Q --- global
  for i=1:M_num
    color = im(Q(i,1),Q(i,2),:);
    %i-k
    i_k = im - repmat(color,h,w);
    i_k_value = calc_delta(i_k);
    val = val + lambda.*i_k_value;
  end
  %calculate the left side
  MI = sparse([1:h*w],[1:h*w],M_num);
  P = sparse([1:h*w],[1:h*w],0);
  for i=1:M_num
    index = (Q(i,1)-1)*w + Q(i,2);
    P(:,index) = -1;
  end
  %P
  Left = (1-lambda).*S + lambda.*(MI + P);
  linear = Left\(val'(:));
end

function d = calc_delta(diff)
  tmp = sum(diff.^3,3)/3;
  d = (0-(tmp<0)+(tmp>=0)).*abs(tmp).^(1/3);
end
