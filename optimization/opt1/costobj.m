function [ c ] = costobj( p,u )
%UNTITLED4 Summary of this function goes here
%   Detailed explanation goes here
    J = u*(1-p).^u;
    for v = 1:u        
        binom = double(evalin(symengine, sprintf('binomial(%d, %d)', u, v)));
        J = J+v*binom.* (p.^v) .* (1-p).^(u-v);
    end
    
    c = J;
end

