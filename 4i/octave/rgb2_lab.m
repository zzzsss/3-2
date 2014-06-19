% rgb to gray %
source calc_landmark2.m;
source get_mat.m;
source rgb2lab.m;

function what = main(Filename)
  im = im2double(imread(Filename));
  [n,m,ch] = size(im);
  if(ch==1)
    im = repmat(im,[1,1,3]);
  end
  figure;
  imshow(im);
  %to lab
  im_lab = RGB2Lab(im);
  out = rgb2gray(im_lab,im);
  a = min(out(:));
  b = max(out(:));
  out = (out-a)/(b-a);
  figure;
  imshow(out);
  what = out;
end

function out = rgb2gray(img_in,im_rgb)
  [height,width,ch] = size(img_in);
  A = get_mat(height,width);
  tic;
  val = get_val(img_in,height,width,im_rgb);
  out = reshape(A\(val'(:)),width,height);
  toc;
  out = out';
end

function val = get_val(im,h,w,im_rgb)
  %parameters
  lambda = 0.8;
  M_num = 30;
  b = 3;
  %calculate
  Q = calc_landmark(im_rgb,b,M_num);
  %% first the ij-neigbour
  %four directions
  d1 = [im(:,1:end-1,:) - im(:,2:end,:),zeros(h,1,3)];
  d2 = [zeros(h,1,3),im(:,2:end,:) - im(:,1:end-1,:)];
  d3 = [zeros(1,w,3);im(2:end,:,:) - im(1:end-1,:,:)];
  d4 = [im(1:end-1,:,:) - im(2:end,:,:);zeros(1,w,3)];
  val = (1-lambda).*(calc_delta(d1) + calc_delta(d2) + calc_delta(d3) + calc_delta(d4));
  %for the Q --- global
  number = 4.*ones(h,w);
  number(1,:) -= 1;
  number(:,1) -= 1;
  number(h,:) -= 1;
  number(:,w) -= 1;
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
  d = calc_delta_lab(diff);
end

function d = calc_delta_lab(diff)
  %0.2 -- 0.6
  wa = wb = 0.2;
  tmp = diff.^3;
  tmp = (tmp(:,:,1)+wa.*tmp(:,:,2)+wb.*tmp(:,:,3));
  d = (0-(tmp<0)+(tmp>=0)).*abs(tmp).^(1/3);
end